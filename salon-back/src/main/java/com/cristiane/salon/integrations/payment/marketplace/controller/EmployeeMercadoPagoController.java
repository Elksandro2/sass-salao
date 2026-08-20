package com.cristiane.salon.integrations.payment.marketplace.controller;

import com.cristiane.salon.annotation.Auditable;
import com.cristiane.salon.integrations.payment.marketplace.EmployeeMercadoPagoConnectionService;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoConnectResponse;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoStatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequiredArgsConstructor
@Tag(name = "Employee Mercado Pago", description = "Conexão OAuth da conta Mercado Pago de cada funcionária, pro split de pagamento")
public class EmployeeMercadoPagoController {

    private final EmployeeMercadoPagoConnectionService connectionService;

    @Value("${app.frontend-url}")
    private String frontendUrl;

    // --- Self-service (Meu Perfil): a própria funcionária conecta/desconecta a conta dela,
    // sem depender da Admin clicar por ela em Admin → Equipe. "me" é literal na rota — o Spring
    // prioriza esse mapeamento exato sobre "/{id}/mercadopago/..." pra qualquer chamada com
    // "me" no lugar do id, então não há ambiguidade de rota entre os dois. ---

    @GetMapping("/v1/employees/me/mercadopago/connect")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Gera o link de autorização OAuth pra funcionária logada conectar a própria conta Mercado Pago")
    public ResponseEntity<MercadoPagoConnectResponse> connectMe() {
        return ResponseEntity.ok(connectionService.generateAuthorizationUrlForCurrentUser());
    }

    @GetMapping("/v1/employees/me/mercadopago/status")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Consulta se a funcionária logada já conectou a própria conta Mercado Pago")
    public ResponseEntity<MercadoPagoStatusResponse> statusMe() {
        return ResponseEntity.ok(connectionService.getStatusForCurrentUser());
    }

    @DeleteMapping("/v1/employees/me/mercadopago")
    @PreAuthorize("isAuthenticated()")
    @Auditable(action = "EMPLOYEE_MP_ACCOUNT_DISCONNECTED", entityType = "Employee", captureArgs = false)
    @Operation(summary = "A funcionária logada desconecta a própria conta Mercado Pago")
    public ResponseEntity<Void> disconnectMe() {
        connectionService.disconnectForCurrentUser();
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/v1/employees/{id}/mercadopago/connect")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Gera o link de autorização OAuth pra funcionária conectar a própria conta Mercado Pago")
    public ResponseEntity<MercadoPagoConnectResponse> connect(@PathVariable Long id) {
        return ResponseEntity.ok(connectionService.generateAuthorizationUrl(id));
    }

    @GetMapping("/v1/employees/{id}/mercadopago/status")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Operation(summary = "Consulta se a funcionária já conectou a conta Mercado Pago")
    public ResponseEntity<MercadoPagoStatusResponse> status(@PathVariable Long id) {
        return ResponseEntity.ok(connectionService.getStatus(id));
    }

    @DeleteMapping("/v1/employees/{id}/mercadopago")
    @PreAuthorize("@verifyUserPermissions.userOwnResourceOrHasPermission(null)")
    @Auditable(action = "EMPLOYEE_MP_ACCOUNT_DISCONNECTED", entityType = "Employee", captureArgs = true)
    @Operation(summary = "Desconecta a conta Mercado Pago da funcionária (só localmente — revogar de vez é feito na conta MP dela)")
    public ResponseEntity<Void> disconnect(@PathVariable Long id) {
        connectionService.disconnect(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * O Mercado Pago redireciona o NAVEGADOR da funcionária pra cá depois que ela autoriza —
     * não é uma chamada de API do frontend, por isso não tem autenticação própria (quem
     * autentica essa operação é o "state" validado dentro do service, gerado só pra quem já
     * estava autenticado no passo anterior).
     */
    @GetMapping("/v1/employees/mercadopago/callback")
    @Auditable(action = "EMPLOYEE_MP_ACCOUNT_CONNECTED", entityType = "Employee", captureArgs = false)
    @Operation(summary = "Callback do OAuth do Mercado Pago (uso interno, não é chamado pelo frontend)")
    public ResponseEntity<Void> callback(@RequestParam String code, @RequestParam String state) {
        // É o navegador da funcionária sendo redirecionado aqui, não uma chamada de API do
        // frontend — devolver um JSON de erro cru deixaria ela travada numa tela em branco.
        // Sempre volta pro app, com o resultado (sucesso ou erro) numa query string.
        try {
            var result = connectionService.handleCallback(code, state);
            String path = "profile".equals(result.redirectTarget()) ? "/admin/profile" : "/admin/team";
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + path + "?mp_connected=" + result.employeeId()))
                    .build();
        } catch (Exception e) {
            return ResponseEntity.status(302)
                    .location(URI.create(frontendUrl + "/admin/team?mp_error=1"))
                    .build();
        }
    }
}
