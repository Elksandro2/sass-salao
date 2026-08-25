package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoConnectResponse;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoStatusResponse;
import com.cristiane.salon.integrations.payment.marketplace.entity.EmployeeMercadoPagoAccount;
import com.cristiane.salon.integrations.payment.marketplace.repository.EmployeeMercadoPagoAccountRepository;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Orquestra o fluxo de conexão OAuth de uma funcionária com a própria conta Mercado Pago
 * (Fase B do split de pagamento).
 *
 * <p>O "state" do OAuth (obrigatório no protocolo, serve pra amarrar a resposta do MP a quem
 * pediu) fica guardado em memória, não no banco: é um dado transiente de poucos minutos — se o
 * servidor reiniciar no meio de alguém autorizando, ela só clica de novo em "Conectar". Guardar
 * em memória evita criar uma tabela só pra isso.
 */
@Service
@RequiredArgsConstructor
public class EmployeeMercadoPagoConnectionService {

    /** Quanto tempo o "state" fica válido — tempo de sobra pra logar e autorizar no MP. */
    private static final long STATE_TTL_MS = 10 * 60 * 1000;

    private final EmployeeRepository employeeRepository;
    private final EmployeeMercadoPagoAccountRepository mpAccountRepository;
    private final MercadoPagoOAuthGateway oAuthGateway;
    private final MercadoPagoSplitProperties splitProperties;
    private final UserRepository userRepository;
    private final FeatureFlagService featureFlagService;

    private final Map<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    private record PendingState(Long employeeId, String redirectTarget, long expiresAtEpochMs) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtEpochMs;
        }
    }

    /** Onde o navegador volta depois do callback — depende de quem iniciou a conexão. */
    public record CallbackResult(Long employeeId, String redirectTarget) {}

    private void assertSplitConfigured() {
        if (!featureFlagService.isEnabled("MERCADO_PAGO_ATIVO")) {
            throw new BadRequestException("A integração com o Mercado Pago está temporariamente desativada.");
        }
        if (!splitProperties.isConfigured()) {
            throw new BadRequestException(
                    "Split de pagamento não está configurado neste ambiente ainda (faltam credenciais de Aplicação do Mercado Pago)");
        }
    }

    public MercadoPagoConnectResponse generateAuthorizationUrl(Long employeeId) {
        return generateAuthorizationUrl(employeeId, "team");
    }

    private MercadoPagoConnectResponse generateAuthorizationUrl(Long employeeId, String redirectTarget) {
        assertSplitConfigured();

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Funcionária não encontrada");
        }

        pendingStates.values().removeIf(PendingState::isExpired);

        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(employeeId, redirectTarget, System.currentTimeMillis() + STATE_TTL_MS));

        return new MercadoPagoConnectResponse(oAuthGateway.buildAuthorizationUrl(state));
    }

    /**
     * Descobre o ID de funcionária vinculado ao usuário logado — usado pelos endpoints
     * "/me" (Meu Perfil), pra qualquer funcionária/gerente conectar a própria conta Mercado
     * Pago sozinha, sem depender da Admin clicar por ela em Admin → Equipe.
     */
    private Long resolveCurrentEmployeeId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new UnauthorizedException("Usuário não autenticado");
        }
        String email = auth.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new UnauthorizedException("Usuário não autenticado"));
        return employeeRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Você não tem um cadastro de funcionária vinculado"))
                .getId();
    }

    public MercadoPagoConnectResponse generateAuthorizationUrlForCurrentUser() {
        return generateAuthorizationUrl(resolveCurrentEmployeeId(), "profile");
    }

    @Transactional(readOnly = true)
    public MercadoPagoStatusResponse getStatusForCurrentUser() {
        return getStatus(resolveCurrentEmployeeId());
    }

    @Transactional
    public void disconnectForCurrentUser() {
        disconnect(resolveCurrentEmployeeId());
    }

    /**
     * Chamado pelo controller quando o Mercado Pago redireciona de volta com o código de
     * autorização. Devolve o ID da funcionária conectada e pra onde mandar o navegador de
     * volta — Admin → Equipe (se quem iniciou foi a Admin) ou Meu Perfil (se foi a própria
     * funcionária pelo self-service).
     */
    @Transactional
    public CallbackResult handleCallback(String code, String state) {
        assertSplitConfigured();

        PendingState pending = pendingStates.remove(state);
        if (pending == null || pending.isExpired()) {
            throw new BadRequestException("Link de autorização expirado ou inválido — peça pra gerar de novo");
        }

        Employee employee = employeeRepository.findById(pending.employeeId())
                .orElseThrow(() -> new ResourceNotFoundException("Funcionária não encontrada"));

        MercadoPagoOAuthGateway.MercadoPagoTokenResponse token = oAuthGateway.exchangeCodeForToken(code);

        EmployeeMercadoPagoAccount account = mpAccountRepository.findByEmployeeId(employee.getId())
                .orElseGet(EmployeeMercadoPagoAccount::new);
        account.setEmployee(employee);
        account.setMpUserId(String.valueOf(token.user_id()));
        account.setAccessToken(token.access_token());
        account.setRefreshToken(token.refresh_token());
        account.setPublicKey(token.public_key());
        account.setTokenExpiresAt(
                token.expires_in() != null ? Instant.now().plusSeconds(token.expires_in()) : null);
        account.setConnectedAt(Instant.now());

        mpAccountRepository.save(account);

        return new CallbackResult(employee.getId(), pending.redirectTarget());
    }

    @Transactional
    public void disconnect(Long employeeId) {
        mpAccountRepository.findByEmployeeId(employeeId).ifPresentOrElse(
                mpAccountRepository::delete,
                () -> {
                    throw new ResourceNotFoundException("Esta funcionária não tem conta Mercado Pago conectada");
                }
        );
    }

    @Transactional(readOnly = true)
    public MercadoPagoStatusResponse getStatus(Long employeeId) {
        return mpAccountRepository.findByEmployeeId(employeeId)
                .map(acc -> new MercadoPagoStatusResponse(true, acc.getConnectedAt()))
                .orElseGet(MercadoPagoStatusResponse::notConnected);
    }

    /** Alguma folga antes do vencimento real, pra não arriscar o token expirar no meio da chamada. */
    private static final java.time.Duration TOKEN_EXPIRY_MARGIN = java.time.Duration.ofMinutes(5);

    /**
     * Devolve um access token válido da funcionária pra usar no split, renovando com o
     * refresh token se o atual já expirou (ou está perto disso) — o access token do MP dura
     * só algumas horas, e o pagamento pode acontecer bem depois da conexão ter sido feita.
     *
     * <p>Se a renovação falhar (token revogado, funcionária desconectou do lado do MP, etc.),
     * devolve vazio em vez de propagar erro — quem chama cai de volta no fluxo sem split em
     * vez de travar a geração do PIX por causa disso.
     */
    @Transactional
    public Optional<String> resolveValidAccessToken(Long employeeId) {
        Optional<EmployeeMercadoPagoAccount> maybeAccount = mpAccountRepository.findByEmployeeId(employeeId);
        if (maybeAccount.isEmpty()) {
            return Optional.empty();
        }

        EmployeeMercadoPagoAccount account = maybeAccount.get();
        Instant expiresAt = account.getTokenExpiresAt();
        boolean needsRefresh = expiresAt == null || Instant.now().plus(TOKEN_EXPIRY_MARGIN).isAfter(expiresAt);

        if (!needsRefresh) {
            return Optional.of(account.getAccessToken());
        }

        try {
            MercadoPagoOAuthGateway.MercadoPagoTokenResponse token = oAuthGateway.refreshToken(account.getRefreshToken());
            account.setAccessToken(token.access_token());
            account.setRefreshToken(token.refresh_token());
            account.setTokenExpiresAt(
                    token.expires_in() != null ? Instant.now().plusSeconds(token.expires_in()) : null);
            mpAccountRepository.save(account);
            return Optional.of(token.access_token());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
