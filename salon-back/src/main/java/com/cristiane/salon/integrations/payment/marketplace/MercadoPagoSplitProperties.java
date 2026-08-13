package com.cristiane.salon.integrations.payment.marketplace;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Configuração do split de pagamento (Fase B/C) — separada de {@code MercadoPagoConfiguration}
 * de propósito: esta é opcional (o app sobe normalmente sem ela, o split só fica indisponível),
 * enquanto access-token/webhook-secret são obrigatórios pro pagamento simples já existente.
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "mercadopago")
public class MercadoPagoSplitProperties {

    private String clientId;
    private String clientSecret;
    private String oauthRedirectUri;
    private BigDecimal pixFeeRate;

    /** Split só está disponível quando as credenciais de Aplicação foram configuradas. */
    public boolean isConfigured() {
        return notBlank(clientId) && notBlank(clientSecret) && notBlank(oauthRedirectUri);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
