package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoConnectResponse;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoStatusResponse;
import com.cristiane.salon.integrations.payment.marketplace.entity.EmployeeMercadoPagoAccount;
import com.cristiane.salon.integrations.payment.marketplace.repository.EmployeeMercadoPagoAccountRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
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

    private final Map<String, PendingState> pendingStates = new ConcurrentHashMap<>();

    private record PendingState(Long employeeId, long expiresAtEpochMs) {
        boolean isExpired() {
            return System.currentTimeMillis() > expiresAtEpochMs;
        }
    }

    private void assertSplitConfigured() {
        if (!splitProperties.isConfigured()) {
            throw new BadRequestException(
                    "Split de pagamento não está configurado neste ambiente ainda (faltam credenciais de Aplicação do Mercado Pago)");
        }
    }

    public MercadoPagoConnectResponse generateAuthorizationUrl(Long employeeId) {
        assertSplitConfigured();

        if (!employeeRepository.existsById(employeeId)) {
            throw new ResourceNotFoundException("Funcionária não encontrada");
        }

        pendingStates.values().removeIf(PendingState::isExpired);

        String state = UUID.randomUUID().toString();
        pendingStates.put(state, new PendingState(employeeId, System.currentTimeMillis() + STATE_TTL_MS));

        return new MercadoPagoConnectResponse(oAuthGateway.buildAuthorizationUrl(state));
    }

    /**
     * Chamado pelo controller quando o Mercado Pago redireciona de volta com o código de
     * autorização. Devolve o ID da funcionária conectada, pro controller montar o redirect
     * de sucesso pro frontend.
     */
    @Transactional
    public Long handleCallback(String code, String state) {
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

        return employee.getId();
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
}
