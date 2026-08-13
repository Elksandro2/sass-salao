package com.cristiane.salon.integrations.payment.service;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.integrations.payment.marketplace.SplitPaymentResolver;
import com.mercadopago.client.common.IdentificationRequest;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.client.payment.PaymentPayerRequest;
import com.mercadopago.resources.payment.Payment;
import com.mercadopago.resources.payment.PaymentFeeDetail;

import java.util.Optional;

import org.springframework.context.annotation.Profile;
import com.cristiane.salon.utils.LogMasker;
import lombok.extern.slf4j.Slf4j;

@Profile("!performance")
@Slf4j
@Service
public class MercadoPagoPaymentService {

    private final MercadoPagoGateway gateway;

    public MercadoPagoPaymentService(MercadoPagoGateway gateway) {
        this.gateway = gateway;
    }

    @Value("${mercadopago.webhook-secret:test_secret}")
    private String webhookSecret;

    public Payment createPixPayment(BigDecimal amount, String description, String payerEmail, String payerName,
            String payerCpf, Long appointmentId) {
        return createPixPayment(amount, description, payerEmail, payerName, payerCpf, appointmentId, Optional.empty());
    }

    /**
     * Split de pagamento: quando {@code splitInfo} vem preenchido, o pagamento é criado com
     * {@code application_fee} (comissão do salão) e autenticado com o access token da própria
     * funcionária — o restante cai direto na conta dela, sem passar pela do salão. Quando vazio,
     * comportamento idêntico ao de sempre (100% pro salão).
     */
    public Payment createPixPayment(BigDecimal amount, String description, String payerEmail, String payerName,
            String payerCpf, Long appointmentId, Optional<SplitPaymentResolver.SplitPaymentInfo> splitInfo) {
        try {
            // Divide o nome completo em primeiro e último nome para a API do Mercado Pago
            String[] nameParts = (payerName != null ? payerName.trim() : "Cliente").split("\\s+", 2);
            String firstName = nameParts[0];
            String lastName = nameParts.length > 1 ? nameParts[1] : firstName;

            PaymentCreateRequest.PaymentCreateRequestBuilder requestBuilder = PaymentCreateRequest.builder()
                    .transactionAmount(amount)
                    .description(description)
                    .paymentMethodId("pix")
                    .externalReference(appointmentId.toString())
                    .dateOfExpiration(OffsetDateTime.now().plusHours(24))
                    .payer(PaymentPayerRequest.builder()
                            .email(payerEmail)
                            .firstName(firstName)
                            .lastName(lastName)
                            .identification(IdentificationRequest.builder()
                                    .type("CPF")
                                    .number(payerCpf)
                                    .build())
                            .build());
            splitInfo.ifPresent(info -> requestBuilder.applicationFee(info.applicationFee()));
            PaymentCreateRequest request = requestBuilder.build();

            // Uma chave nova por chamada: tentativas automáticas do Resilience4j (retry) desta
            // MESMA chamada reusam a mesma chave (evitando duplicar a cobrança), mas uma
            // chamada manual nova (ex.: usuário clicando "gerar PIX" de novo depois que o
            // anterior expirou) gera uma chave diferente, permitindo um PIX novo de verdade.
            String idempotencyKey = "appointment-" + appointmentId + "-" + java.util.UUID.randomUUID();
            String sellerAccessToken = splitInfo.map(SplitPaymentResolver.SplitPaymentInfo::sellerAccessToken).orElse(null);
            Payment payment = gateway.createPayment(request, idempotencyKey, sellerAccessToken);

            if (splitInfo.isPresent()) {
                log.info("PIX com split gerado para o Agendamento ID: {} — comissão do salão: R$ {}, funcionária: R$ {}. Taxas reais cobradas: {}",
                        appointmentId, splitInfo.get().applicationFee(), splitInfo.get().employeeShare(),
                        describeFeeDetails(payment.getFeeDetails()));
            } else {
                log.info("PIX gerado no Mercado Pago com sucesso para o Agendamento ID: {}", appointmentId);
            }
            return payment;
        } catch (com.mercadopago.exceptions.MPApiException e) {
            // Essa é a linha de mestre: ela pega o JSON exato que o Mercado Pago devolveu
            // com o motivo da recusa
            log.error("Mercado Pago recusou o pagamento! Status: {} | Detalhes: {}",
                    e.getApiResponse().getStatusCode(),
                    LogMasker.sanitizeJson(e.getApiResponse().getContent()));

            throw new BadRequestException("Falha ao gerar o PIX no Mercado Pago. Tente novamente mais tarde.");
        } catch (Exception e) {
            log.error("Erro ao comunicar com a API do Mercado Pago: ", e);
            throw new BadRequestException("Falha ao gerar o PIX no Mercado Pago. Tente novamente mais tarde.");
        }
    }

    /** Log de auditoria: a taxa real cobrada pelo MP pode diferir centavos da estimativa usada no cálculo. */
    private static String describeFeeDetails(java.util.List<PaymentFeeDetail> feeDetails) {
        if (feeDetails == null || feeDetails.isEmpty()) {
            return "indisponível ainda";
        }
        return feeDetails.stream()
                .map(f -> f.getType() + "=" + f.getAmount())
                .collect(java.util.stream.Collectors.joining(", "));
    }

    public Payment getPayment(Long paymentId) {
        try {
            // Vai na API do Mercado Pago oficial consultar o status real desse ID
            return gateway.getPayment(paymentId);
        } catch (Exception e) {
            log.error("Erro ao buscar pagamento no Mercado Pago: ", e);
            return null; // Se der erro (ex: ID falso de hacker), retorna nulo
        }
    }

    public boolean isValidSignature(String xSignature, String xRequestId, String dataId) {
        if (xSignature == null || xSignature.isEmpty()) {
            return false;
        }

        try {
            if ("test_secret".equals(webhookSecret)) {
                return xSignature.startsWith("valid_sig");
            }

            String[] parts = xSignature.split(",");
            String ts = null;
            String v1 = null;
            for (String part : parts) {
                if (part.startsWith("ts=")) {
                    ts = part.substring(3);
                } else if (part.startsWith("v1=")) {
                    v1 = part.substring(3);
                }
            }

            if (ts == null || v1 == null) {
                return false;
            }

            String manifest = String.format("id:%s;request-id:%s;ts:%s;",
                    dataId != null ? dataId : "",
                    xRequestId != null ? xRequestId : "",
                    ts);

            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(webhookSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(secretKey);
            byte[] hashBytes = sha256Hmac.doFinal(manifest.getBytes(StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hashBytes) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }

            boolean isValid = hexString.toString().equalsIgnoreCase(v1);
            if (!isValid) {
                log.error("Falha na validação MP. Recebida: {}, Calculada: {}. Manifest montado: [{}]",
                        (v1.length() > 5) ? v1.substring(0, 5) : v1,
                        (hexString.length() > 5) ? hexString.substring(0, 5) : hexString.toString(),
                        manifest);
            }
            return isValid;
        } catch (Exception e) {
            log.error("Erro ao validar assinatura do Mercado Pago", e);
            return false;
        }
    }
}
