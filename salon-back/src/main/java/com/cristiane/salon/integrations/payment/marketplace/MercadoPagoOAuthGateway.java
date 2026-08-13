package com.cristiane.salon.integrations.payment.marketplace;

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Único ponto de contato com os endpoints OAuth do Mercado Pago (autorização de conta de
 * funcionária pro split de pagamento). Separado do SDK oficial (usado em
 * {@code MercadoPagoPaymentService}) porque o SDK não cobre o fluxo OAuth de marketplace —
 * aqui é chamada HTTP direta, como {@code EmailGateway} faz com o Resend.
 */
@Component
@RequiredArgsConstructor
public class MercadoPagoOAuthGateway {

    private static final String AUTH_BASE_URL = "https://auth.mercadopago.com";
    private static final String API_BASE_URL = "https://api.mercadopago.com";

    private final RestClient.Builder restClientBuilder;
    private final MercadoPagoSplitProperties properties;

    /** URL pra onde a funcionária é redirecionada pra logar na própria conta MP e autorizar. */
    public String buildAuthorizationUrl(String state) {
        return AUTH_BASE_URL + "/authorization"
                + "?client_id=" + properties.getClientId()
                + "&response_type=code"
                + "&platform_id=mp"
                + "&redirect_uri=" + properties.getOauthRedirectUri()
                + "&state=" + state;
    }

    /** Troca o código de autorização (devolvido no callback) pelos tokens de acesso. */
    @CircuitBreaker(name = "mercadopago-oauth")
    @Retry(name = "mercadopago-oauth")
    public MercadoPagoTokenResponse exchangeCodeForToken(String code) {
        RestClient restClient = restClientBuilder.clone().baseUrl(API_BASE_URL).build();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client_id", properties.getClientId());
        payload.put("client_secret", properties.getClientSecret());
        payload.put("grant_type", "authorization_code");
        payload.put("code", code);
        payload.put("redirect_uri", properties.getOauthRedirectUri());

        return restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(MercadoPagoTokenResponse.class);
    }

    /**
     * Renova o access token de uma funcionária usando o refresh token guardado — necessário
     * porque o access token do MP expira em poucas horas (ver {@code expires_in} na resposta),
     * e um pagamento pode acontecer dias depois da conexão ter sido feita.
     */
    @CircuitBreaker(name = "mercadopago-oauth")
    @Retry(name = "mercadopago-oauth")
    public MercadoPagoTokenResponse refreshToken(String refreshToken) {
        RestClient restClient = restClientBuilder.clone().baseUrl(API_BASE_URL).build();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("client_id", properties.getClientId());
        payload.put("client_secret", properties.getClientSecret());
        payload.put("grant_type", "refresh_token");
        payload.put("refresh_token", refreshToken);

        return restClient.post()
                .uri("/oauth/token")
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .body(MercadoPagoTokenResponse.class);
    }

    /** Resposta do MP na troca de código por token — nomes de campo batem com o payload deles. */
    public record MercadoPagoTokenResponse(
            String access_token,
            String token_type,
            Integer expires_in,
            String scope,
            Long user_id,
            String refresh_token,
            String public_key
    ) {
    }
}
