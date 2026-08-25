package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoConnectResponse;
import com.cristiane.salon.integrations.payment.marketplace.dto.MercadoPagoStatusResponse;
import com.cristiane.salon.integrations.payment.marketplace.entity.EmployeeMercadoPagoAccount;
import com.cristiane.salon.integrations.payment.marketplace.repository.EmployeeMercadoPagoAccountRepository;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

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

    @Mock
    private UserRepository userRepository;

    @Mock
    private FeatureFlagService featureFlagService;

    private MercadoPagoSplitProperties splitProperties;
    private EmployeeMercadoPagoConnectionService service;

    private Employee employee;

    @BeforeEach
    void setUp() {
        splitProperties = new MercadoPagoSplitProperties();
        splitProperties.setClientId("client-id");
        splitProperties.setClientSecret("client-secret");
        splitProperties.setOauthRedirectUri("https://example.com/callback");

        lenient().when(featureFlagService.isEnabled("ENABLE_MERCADO_PAGO")).thenReturn(true);

        service = new EmployeeMercadoPagoConnectionService(
                employeeRepository, mpAccountRepository, oAuthGateway, splitProperties, userRepository, featureFlagService);

        employee = new Employee();
        employee.setId(5L);
        employee.setUser(new User());
        employee.getUser().setId(10L);
    }

    private void mockAuthenticatedUser(User user) {
        var auth = new UsernamePasswordAuthenticationToken(user.getEmail(), null, java.util.List.of());
        var secCtx = SecurityContextHolder.createEmptyContext();
        secCtx.setAuthentication(auth);
        SecurityContextHolder.setContext(secCtx);
    }

    @Test
    void generateAuthorizationUrl_whenFeatureFlagDisabled_shouldThrowBadRequestException() {
        when(featureFlagService.isEnabled("ENABLE_MERCADO_PAGO")).thenReturn(false);

        assertThatThrownBy(() -> service.generateAuthorizationUrl(5L))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("temporariamente desativada");
        verifyNoInteractions(employeeRepository);
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

        var result = service.handleCallback("auth-code", state);

        assertThat(result.employeeId()).isEqualTo(5L);
        assertThat(result.redirectTarget()).isEqualTo("team");

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

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void generateAuthorizationUrlForCurrentUser_shouldResolveEmployeeFromLoggedInUserAndTagRedirectAsProfile() {
        employee.getUser().setEmail("funcionaria@example.com");
        mockAuthenticatedUser(employee.getUser());
        when(userRepository.findByEmail("funcionaria@example.com")).thenReturn(Optional.of(employee.getUser()));
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.of(employee));
        when(employeeRepository.existsById(5L)).thenReturn(true);
        when(oAuthGateway.buildAuthorizationUrl(any()))
                .thenAnswer(inv -> "https://auth.mercadopago.com/authorization?state=" + inv.getArgument(0));

        MercadoPagoConnectResponse response = service.generateAuthorizationUrlForCurrentUser();
        String state = extractState(response);

        when(employeeRepository.findById(5L)).thenReturn(Optional.of(employee));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());
        when(oAuthGateway.exchangeCodeForToken(any())).thenReturn(
                new MercadoPagoOAuthGateway.MercadoPagoTokenResponse(
                        "access-123", "bearer", 21600, "read write", 999L, "refresh-456", "PUB-KEY"));

        var result = service.handleCallback("auth-code", state);

        assertThat(result.employeeId()).isEqualTo(5L);
        assertThat(result.redirectTarget()).isEqualTo("profile");
    }

    @Test
    void generateAuthorizationUrlForCurrentUser_whenUserHasNoLinkedEmployee_shouldThrowResourceNotFoundException() {
        User adminUser = new User();
        adminUser.setId(20L);
        adminUser.setEmail("admin@example.com");
        mockAuthenticatedUser(adminUser);
        when(userRepository.findByEmail("admin@example.com")).thenReturn(Optional.of(adminUser));
        when(employeeRepository.findByUserId(20L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.generateAuthorizationUrlForCurrentUser())
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("não tem um cadastro de funcionária");
    }

    @Test
    void generateAuthorizationUrlForCurrentUser_whenNotAuthenticated_shouldThrowUnauthorizedException() {
        assertThatThrownBy(() -> service.generateAuthorizationUrlForCurrentUser())
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void getStatusForCurrentUser_shouldUseLoggedInUsersEmployeeId() {
        employee.getUser().setEmail("funcionaria@example.com");
        mockAuthenticatedUser(employee.getUser());
        when(userRepository.findByEmail("funcionaria@example.com")).thenReturn(Optional.of(employee.getUser()));
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.of(employee));
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.empty());

        MercadoPagoStatusResponse result = service.getStatusForCurrentUser();

        assertThat(result.connected()).isFalse();
    }

    @Test
    void disconnectForCurrentUser_shouldUseLoggedInUsersEmployeeId() {
        employee.getUser().setEmail("funcionaria@example.com");
        mockAuthenticatedUser(employee.getUser());
        when(userRepository.findByEmail("funcionaria@example.com")).thenReturn(Optional.of(employee.getUser()));
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.of(employee));
        EmployeeMercadoPagoAccount account = new EmployeeMercadoPagoAccount();
        when(mpAccountRepository.findByEmployeeId(5L)).thenReturn(Optional.of(account));

        service.disconnectForCurrentUser();

        verify(mpAccountRepository).delete(account);
    }

    private String extractState(MercadoPagoConnectResponse response) {
        String url = response.authorizationUrl();
        int idx = url.indexOf("state=");
        return url.substring(idx + "state=".length());
    }
}
