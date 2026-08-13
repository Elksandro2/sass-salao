package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoConnectResponse;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoStatusResponse;
import com.cristiane.salon.integrations.payment.marketplace.entity.EmployeeMercadoPagoAccount;
import com.cristiane.salon.integrations.payment.marketplace.repository.EmployeeMercadoPagoAccountRepository;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeMercadoPagoConnectionServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private EmployeeMercadoPagoAccountRepository mpAccountRepository;

    @Mock
    private MercadoPagoOAuthGateway oAuthGateway;

    private MercadoPagoSplitProperties splitProperties;
    private EmployeeMercadoPagoConnectionService service;

    private Employee employee;

    @BeforeEach
    void setUp() {
        splitProperties = new MercadoPagoSplitProperties();
        splitProperties.setClientId("client-id");
        splitProperties.setClientSecret("client-secret");
        splitProperties.setOauthRedirectUri("https://example.com/callback");

        service = new EmployeeMercadoPagoConnectionService(
                employeeRepository, mpAccountRepository, oAuthGateway, splitProperties);

        employee = new Employee();
        employee.setId(5L);
        employee.setUser(new User());
        employee.getUser().setId(10L);
    }

    @Test
    void generateAuthorizationUrl_whenSplitNotConfigured_shouldThrowBadRequestException() {
        splitProperties.setClientId(null);

        assertThatThrownBy(() -> service.generateAuthorizationUrl(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não está configurado");
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void generateAuthorizationUrl_whenEmployeeNotFound_shouldThrowResourceNotFoundException() {
        when(employeeRepository.existsById(99L)).thenReturn(false);

        assertThatThrownBy(() -> service.generateAuthorizationUrl(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void generateAuthorizationUrl_whenValid_shouldReturnUrlFromGateway() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(oAuthGateway.buildAuthorizationUrl(any())).thenReturn("https://auth.mercadopago.com/authorization?state=abc");

        MercadoPagoConnectResponse result = service.generateAuthorizationUrl(5L);

        assertThat(result.authorizationUrl()).contains("auth.mercadopago.com");
        verify(oAuthGateway).buildAuthorizationUrl(any());
    }

    @Test
    void handleCallback_whenStateInvalid_shouldThrowBadRequestException() {
        assertThatThrownBy(() -> service.handleCallback("some-code", "unknown-state"))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("expirado ou inválido");
        verifyNoInteractions(oAuthGateway);
    }

    @Test
    void handleCallback_whenStateValid_shouldExchangeTokenAndSaveAccount() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(oAuthGateway.buildAuthorizationUrl(any()))
                .thenAnswer(inv -> "https://auth.mercadopago.com/authorization?state=" + inv.getArgument(0));
        MercadoPagoConnectResponse connectResponse = service.generateAuthorizationUrl(5L);
        String state = extractState(connectResponse);

        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(oAuthGateway.exchangeCodeForToken("auth-code")).thenReturn(
                new MercadoPagoOAuthGateway.MercadoPagoTokenResponse(
                        "access-123", "bearer", 21600, "read write", 999L, "refresh-456", "PUB-KEY"));

        Long resultEmployeeId = service.handleCallback("auth-code", state);

        assertThat(resultEmployeeId).isEqualTo(5L);

        ArgumentCaptor<EmployeeMercadoPagoAccount> captor = ArgumentCaptor.forClass(EmployeeMercadoPagoAccount.class);
        verify(mpAccountRepository).save(captor.capture());
        EmployeeMercadoPagoAccount saved = captor.getValue();
        assertThat(saved.getMpUserId()).isEqualTo("999");
        assertThat(saved.getAccessToken()).isEqualTo("access-123");
        assertThat(saved.getRefreshToken()).isEqualTo("refresh-456");
        assertThat(saved.getPublicKey()).isEqualTo("PUB-KEY");
        assertThat(saved.getEmployee()).isEqualTo(employee);
    }

    @Test
    void handleCallback_whenStateAlreadyUsed_shouldThrowOnSecondAttempt() {
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(oAuthGateway.buildAuthorizationUrl(any()))
                .thenAnswer(inv -> "https://auth.mercadopago.com/authorization?state=" + inv.getArgument(0));
        MercadoPagoConnectResponse connectResponse = service.generateAuthorizationUrl(5L);
        String state = extractState(connectResponse);

        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(oAuthGateway.exchangeCodeForToken(any())).thenReturn(
                new MercadoPagoOAuthGateway.MercadoPagoTokenResponse(
                        "access-123", "bearer", 21600, "read write", 999L, "refresh-456", "PUB-KEY"));

        service.handleCallback("auth-code", state);

        assertThatThrownBy(() -> service.handleCallback("auth-code", state))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void disconnect_whenExists_shouldDelete() {
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));

        service.disconnect(5L);

        verify(mpAccountRepository).delete(account);
    }

    @Test
    void disconnect_whenNotFound_shouldThrowResourceNotFoundException() {
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.disconnect(5L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void getStatus_whenConnected_shouldReturnTrue() {
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        account.setConnectedAt(java.time.Instant.parse("2026-01-01T00:00:00Z"));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));

        MercadoPagoStatusResponse result = service.getStatus(5L);

        assertThat(result.connected()).isTrue();
        assertThat(result.connectedAt()).isEqualTo(java.time.Instant.parse("2026-01-01T00:00:00Z"));
    }

    @Test
    void getStatus_whenNotConnected_shouldReturnFalse() {
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

        MercadoPagoStatusResponse result = service.getStatus(5L);

        assertThat(result.connected()).isFalse();
        assertThat(result.connectedAt()).isNull();
    }

    @Test
    void resolveValidAccessToken_whenNotConnected_shouldReturnEmpty() {
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

        assertThat(service.resolveValidAccessToken(5L)).isEmpty();
        verifyNoInteractions(oAuthGateway);
    }

    @Test
    void resolveValidAccessToken_whenTokenStillValid_shouldReturnItWithoutRefreshing() {
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        account.setAccessToken("valid-token");
        account.setTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofHours(2)));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));

        assertThat(service.resolveValidAccessToken(5L)).contains("valid-token");
        verifyNoInteractions(oAuthGateway);
        verify(mpAccountRepository, never()).save(any());
    }

    @Test
    void resolveValidAccessToken_whenTokenExpiringSoon_shouldRefreshAndPersist() {
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        account.setAccessToken("old-token");
        account.setRefreshToken("refresh-token");
        // dentro da margem de 5 minutos — precisa renovar mesmo "ainda não vencido"
        account.setTokenExpiresAt(java.time.Instant.now().plus(java.time.Duration.ofMinutes(1)));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));
        when(oAuthGateway.refreshToken("refresh-token")).thenReturn(
                new MercadoPagoOAuthGateway.MercadoPagoTokenResponse(
                        "new-token", "bearer", 21600, "read write", 999L, "new-refresh-token", "PUB-KEY"));

        assertThat(service.resolveValidAccessToken(5L)).contains("new-token");
        assertThat(account.getAccessToken()).isEqualTo("new-token");
        assertThat(account.getRefreshToken()).isEqualTo("new-refresh-token");
        verify(mpAccountRepository).save(account);
    }

    @Test
    void resolveValidAccessToken_whenTokenExpiredAndRefreshFails_shouldReturnEmpty() {
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        account.setAccessToken("old-token");
        account.setRefreshToken("revoked-refresh-token");
        account.setTokenExpiresAt(java.time.Instant.now().minus(java.time.Duration.ofHours(1)));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));
        when(oAuthGateway.refreshToken("revoked-refresh-token")).thenThrow(new RuntimeException("token revogado"));

        assertThat(service.resolveValidAccessToken(5L)).isEmpty();
        verify(mpAccountRepository, never()).save(any());
    }

    @Test
    void resolveValidAccessToken_whenExpiresAtIsNull_shouldRefresh() {
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        account.setAccessToken("old-token");
        account.setRefreshToken("refresh-token");
        account.setTokenExpiresAt(null);
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));
        when(oAuthGateway.refreshToken("refresh-token")).thenReturn(
                new MercadoPagoOAuthGateway.MercadoPagoTokenResponse(
                        "new-token", "bearer", 21600, "read write", 999L, "new-refresh-token", "PUB-KEY"));

        assertThat(service.resolveValidAccessToken(5L)).contains("new-token");
    }

    private String extractState(MercadoPagoConnectResponse response) {
        String url = response.authorizationUrl();
        int idx = url.indexOf("state=");
        return url.substring(idx + "state=".length());
    }
}
