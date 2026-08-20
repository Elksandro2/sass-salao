package com.cristiane.salon.models.user.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ConflictException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.audit.AuditLogService;
import com.cristiane.salon.models.featureflag.service.FeatureFlagService;
import com.cristiane.salon.models.user.dto.LoginRequest;
import com.cristiane.salon.models.user.dto.RegisterRequest;
import com.cristiane.salon.models.user.dto.TokenResponse;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.RoleRepository;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtService jwtService;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private AuditLogService auditLogService;

    @Mock
    private FeatureFlagService featureFlagService;

    @InjectMocks
    private AuthService authService;

    private User activeUser;
    private User inactiveUser;
    private Role clientRole;

    @BeforeEach
    void setUp() {
        clientRole = new Role(1L, "CLIENTE", null);

        activeUser = new User();
        activeUser.setId(10L);
        activeUser.setName("Carlos");
        activeUser.setEmail("carlos@example.com");
        activeUser.setPassword("encoded_pass");
        activeUser.setPhone("81999999999");
        activeUser.setActive(true);
        activeUser.setRole(clientRole);

        inactiveUser = new User();
        inactiveUser.setId(11L);
        inactiveUser.setName("Joana");
        inactiveUser.setEmail("joana@example.com");
        inactiveUser.setPassword("encoded_pass");
        inactiveUser.setPhone("81988888888");
        inactiveUser.setActive(false);
        inactiveUser.setRole(clientRole);
    }

    @Test
    void register_whenSuccessful_shouldSaveUserGenerateTokensAndLogSuccess() {
        // Arrange
        RegisterRequest request = new RegisterRequest("Carlos", "carlos@example.com", "password", "81999999999");
        when(featureFlagService.isEnabled("ENABLE_SELF_REGISTRATION")).thenReturn(true);
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("CLIENTE")).thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode("password")).thenReturn("encoded_pass");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(10L);
            return user;
        });
        when(jwtService.generateAccessToken(any(User.class))).thenReturn("access_token");
        when(jwtService.generateRefreshToken(any(User.class))).thenReturn("refresh_token");

        // Act
        TokenResponse response = authService.register(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token");
        assertThat(response.refreshToken()).isEqualTo("refresh_token");

        verify(userRepository).save(any(User.class));
        verify(auditLogService).logAction(
                eq(10L),
                eq("carlos@example.com"),
                eq("REGISTER"),
                eq("User"),
                eq(10L),
                eq("Cadastro efetuado com sucesso para: carlos@example.com"),
                eq("SUCCESS")
        );
    }

    @Test
    void register_whenEmailAlreadyExists_shouldThrowConflictExceptionAndLogFailure() {
        // Arrange
        RegisterRequest request = new RegisterRequest("Carlos", "carlos@example.com", "password", "81999999999");
        when(featureFlagService.isEnabled("ENABLE_SELF_REGISTRATION")).thenReturn(true);
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(activeUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email já cadastrado");

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService).logAction(
                isNull(),
                eq("carlos@example.com"),
                eq("REGISTER"),
                eq("User"),
                isNull(),
                eq("Falha ao realizar cadastro para o e-mail: carlos@example.com"),
                eq("FAILURE"),
                eq("Email já cadastrado")
        );
    }

    @Test
    void register_whenRoleClientNotFound_shouldThrowResourceNotFoundExceptionAndLogFailure() {
        // Arrange
        RegisterRequest request = new RegisterRequest("Carlos", "carlos@example.com", "password", "81999999999");
        when(featureFlagService.isEnabled("ENABLE_SELF_REGISTRATION")).thenReturn(true);
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findByName("CLIENTE")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Role CLIENTE não encontrada");

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService).logAction(
                isNull(),
                eq("carlos@example.com"),
                eq("REGISTER"),
                eq("User"),
                isNull(),
                eq("Falha ao realizar cadastro para o e-mail: carlos@example.com"),
                eq("FAILURE"),
                eq("Role CLIENTE não encontrada")
        );
    }

    @Test
    void register_whenSelfRegistrationDisabled_shouldThrowBadRequestExceptionWithoutSavingUser() {
        // Arrange
        RegisterRequest request = new RegisterRequest("Carlos", "carlos@example.com", "password", "81999999999");
        when(featureFlagService.isEnabled("ENABLE_SELF_REGISTRATION")).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O auto-cadastro está desativado no momento.");

        verify(userRepository, never()).save(any(User.class));
        verify(auditLogService, never()).logAction(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void login_whenSuccessful_shouldAuthenticateGenerateTokensAndLogSuccess() {
        // Arrange
        LoginRequest request = new LoginRequest("carlos@example.com", "password");
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(activeUser));
        when(jwtService.generateAccessToken(activeUser)).thenReturn("access_token");
        when(jwtService.generateRefreshToken(activeUser)).thenReturn("refresh_token");

        // Act
        TokenResponse response = authService.login(request);

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("access_token");
        assertThat(response.refreshToken()).isEqualTo("refresh_token");

        verify(authenticationManager).authenticate(
                new UsernamePasswordAuthenticationToken("carlos@example.com", "password")
        );
        verify(auditLogService).logAction(
                eq(10L),
                eq("carlos@example.com"),
                eq("LOGIN"),
                eq("User"),
                eq(10L),
                eq("Login efetuado com sucesso pelo usuário: carlos@example.com"),
                eq("SUCCESS")
        );
    }

    @Test
    void login_whenUserNotFound_shouldThrowUnauthorizedExceptionAndLogFailure() {
        // Arrange
        LoginRequest request = new LoginRequest("unknown@example.com", "password");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Usuário não encontrado");

        verify(authenticationManager, never()).authenticate(any());
        verify(auditLogService).logAction(
                isNull(),
                eq("unknown@example.com"),
                eq("LOGIN"),
                eq("User"),
                isNull(),
                eq("Falha na tentativa de login com o e-mail: unknown@example.com"),
                eq("FAILURE"),
                eq("Usuário não encontrado")
        );
    }

    @Test
    void login_whenUserInactive_shouldThrowUnauthorizedExceptionAndLogFailure() {
        // Arrange
        LoginRequest request = new LoginRequest("joana@example.com", "password");
        when(userRepository.findByEmail("joana@example.com")).thenReturn(Optional.of(inactiveUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Sua conta está inativa");

        verify(authenticationManager, never()).authenticate(any());
        verify(auditLogService).logAction(
                eq(11L),
                eq("joana@example.com"),
                eq("LOGIN"),
                eq("User"),
                eq(11L),
                eq("Falha na tentativa de login com o e-mail: joana@example.com"),
                eq("FAILURE"),
                eq("Sua conta está inativa")
        );
    }

    @Test
    void login_whenAuthenticationFails_shouldThrowExceptionAndLogFailure() {
        // Arrange
        LoginRequest request = new LoginRequest("carlos@example.com", "wrong_password");
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(activeUser));
        doThrow(new BadCredentialsException("Senha incorreta")).when(authenticationManager)
                .authenticate(any(UsernamePasswordAuthenticationToken.class));

        // Act & Assert
        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Senha incorreta");

        verify(auditLogService).logAction(
                eq(10L),
                eq("carlos@example.com"),
                eq("LOGIN"),
                eq("User"),
                eq(10L),
                eq("Falha na tentativa de login com o e-mail: carlos@example.com"),
                eq("FAILURE"),
                eq("Senha incorreta")
        );
    }

    @Test
    void refresh_whenTokenValid_shouldReturnNewAccessToken() {
        // Arrange
        when(jwtService.extractUsername("valid_refresh_token")).thenReturn("carlos@example.com");
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(activeUser));
        when(jwtService.isTokenValid("valid_refresh_token", activeUser)).thenReturn(true);
        when(jwtService.generateAccessToken(activeUser)).thenReturn("new_access_token");

        // Act
        TokenResponse response = authService.refresh("valid_refresh_token");

        // Assert
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("new_access_token");
        assertThat(response.refreshToken()).isEqualTo("valid_refresh_token");
    }

    @Test
    void refresh_whenUsernameExtractionThrowsException_shouldThrowUnauthorizedException() {
        // Arrange
        when(jwtService.extractUsername("invalid_token")).thenThrow(new RuntimeException("Token parse failure"));

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh("invalid_token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token inválido");
    }

    @Test
    void refresh_whenExtractedUsernameIsNull_shouldThrowUnauthorizedException() {
        // Arrange
        when(jwtService.extractUsername("null_username_token")).thenReturn(null);

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh("null_username_token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token expirado ou inválido");
    }

    @Test
    void refresh_whenUserNotFound_shouldThrowUnauthorizedException() {
        // Arrange
        when(jwtService.extractUsername("some_token")).thenReturn("unknown@example.com");
        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh("some_token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void refresh_whenUserInactive_shouldThrowUnauthorizedException() {
        // Arrange
        when(jwtService.extractUsername("some_token")).thenReturn("joana@example.com");
        when(userRepository.findByEmail("joana@example.com")).thenReturn(Optional.of(inactiveUser));

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh("some_token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Sua conta está inativa");
    }

    @Test
    void refresh_whenTokenInvalid_shouldThrowUnauthorizedException() {
        // Arrange
        when(jwtService.extractUsername("some_token")).thenReturn("carlos@example.com");
        when(userRepository.findByEmail("carlos@example.com")).thenReturn(Optional.of(activeUser));
        when(jwtService.isTokenValid("some_token", activeUser)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> authService.refresh("some_token"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Refresh token expirado ou inválido");
    }
}
