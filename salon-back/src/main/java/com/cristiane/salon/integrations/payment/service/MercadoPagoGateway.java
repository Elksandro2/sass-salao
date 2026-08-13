package com.cristiane.salon.integrations.payment.service;

import com.mercadopago.client.payment.PaymentClient;
import com.mercadopago.client.payment.PaymentCreateRequest;
import com.mercadopago.core.MPRequestOptions;
import com.mercadopago.resources.payment.Payment;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Único ponto de contato com o SDK do Mercado Pago. Isolado em seu próprio bean (em vez de
 * viver dentro de {@link MercadoPagoPaymentService}) porque as anotações de resiliência do
 * Resilience4j são aplicadas via proxy do Spring — uma chamada de um método para outro dentro
 * da MESMA classe não passa pelo proxy, então @CircuitBreaker/@Retry seriam ignorados se
 * ficassem num método privado chamado internamente por {@link MercadoPagoPaymentService}.
 *
 * {@link MercadoPagoPaymentService} continua responsável por mapear qualquer falha (rede,
 * timeout, circuito aberto, recusa de negócio) para {@code BadRequestException} — este gateway
 * só transporta e deixa a exceção original escapar, para o Resilience4j conseguir diferenciar
 * falha transitória (retry vale a pena) de recusa de negócio (retry não adianta).
 */
@Profile("!performance")
@Component
public class MercadoPagoGateway {

    @CircuitBreaker(name = "mercadopago")
    @Retry(name = "mercadopago")
    public Payment createPayment(PaymentCreateRequest request, String idempotencyKey) throws Exception {
        return createPayment(request, idempotencyKey, null);
    }

    /**
     * Split de pagamento: quando {@code sellerAccessToken} vem preenchido, a chamada à API é
     * autenticada com o token da PRÓPRIA funcionária (obtido via OAuth), não com o token global
     * do salão — é assim que o Mercado Pago sabe creditar a conta dela. O {@code application_fee}
     * (comissão do salão) já vem calculado dentro do {@code request}.
     */
    @CircuitBreaker(name = "mercadopago")
    @Retry(name = "mercadopago")
    public Payment createPayment(PaymentCreateRequest request, String idempotencyKey, String sellerAccessToken) throws Exception {
        // Sem isso, um timeout que dispara DEPOIS do Mercado Pago já ter processado o pagamento
        // (mas antes da resposta chegar) faria o @Retry criar um SEGUNDO PIX para o mesmo
        // agendamento — cobrança duplicada de verdade, não hipotética. A chave é gerada uma
        // única vez por chamada de MercadoPagoPaymentService.createPixPayment e reutilizada em
        // todas as tentativas automáticas do Resilience4j dessa MESMA chamada, então o Mercado
        // Pago reconhece as tentativas repetidas como "a mesma operação" e devolve o pagamento
        // já criado em vez de processar de novo.
        MPRequestOptions.MPRequestOptionsBuilder optionsBuilder = MPRequestOptions.builder()
                .customHeaders(Map.of("X-Idempotency-Key", idempotencyKey));
        if (sellerAccessToken != null) {
            optionsBuilder.accessToken(sellerAccessToken);
        }
        PaymentClient client = new PaymentClient();
        return client.create(request, optionsBuilder.build());
    }

    @CircuitBreaker(name = "mercadopago", fallbackMethod = "getPaymentFallback")
    @Retry(name = "mercadopago")
    public Payment getPayment(Long paymentId) throws Exception {
        PaymentClient client = new PaymentClient();
        return client.get(paymentId);
    }

    // Mantém o comportamento histórico de getPayment: se o Mercado Pago não responder mesmo
    // após retry/circuito, devolve null em vez de propagar (chamador trata id inválido/serviço
    // fora do ar da mesma forma).
    private Payment getPaymentFallback(Long paymentId, Throwable t) {
        return null;
    }
}
