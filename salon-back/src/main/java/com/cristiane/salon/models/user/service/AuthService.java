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
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final AuditLogService auditLogService;
    private final FeatureFlagService featureFlagService;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        // Auto-cadastro desligado por padrão nesta versão: cliente é criado só pelo lado
        // administrativo. Reforça em nível de service, não só escondendo o link na tela de
        // login — chamar o endpoint direto não deve furar essa regra.
        if (!featureFlagService.isEnabled("ENABLE_SELF_REGISTRATION")) {
            throw new BadRequestException("O auto-cadastro está desativado no momento.");
        }
        try {
            if (userRepository.findByEmail(request.email()).isPresent()) {
                throw new ConflictException("Email já cadastrado");
            }

            Role clienteRole = roleRepository.findByName("CLIENTE")
                    .orElseThrow(() -> new ResourceNotFoundException("Role CLIENTE não encontrada"));

            User user = new User();
            user.setName(request.name());
            user.setEmail(request.email());
            user.setPassword(passwordEncoder.encode(request.password()));
            user.setPhone(request.phone());
            user.setRole(clienteRole);
            user.setActive(true);

            userRepository.save(user);

            String jwtToken = jwtService.generateAccessToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            // Registo de auditoria de sucesso
            auditLogService.logAction(
                    user.getId(),
                    user.getEmail(),
                    "REGISTER",
                    "User",
                    user.getId(),
                    "Cadastro efetuado com sucesso para: " + user.getEmail(),
                    "SUCCESS"
            );

            return new TokenResponse(jwtToken, refreshToken);
        } catch (Exception e) {
            // Registo de auditoria de falha
            auditLogService.logAction(
                    null,
                    request.email(),
                    "REGISTER",
                    "User",
                    null,
                    "Falha ao realizar cadastro para o e-mail: " + request.email(),
                    "FAILURE",
                    e.getMessage()
            );
            throw e;
        }
    }

    public TokenResponse login(LoginRequest request) {
        try {
            User user = userRepository.findByEmail(request.email())
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));

            if (!user.getActive()) {
                throw new UnauthorizedException("Sua conta está inativa");
            }

            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.email(), request.password())
            );

            String jwtToken = jwtService.generateAccessToken(user);
            String refreshToken = jwtService.generateRefreshToken(user);

            // Registo de auditoria de sucesso
            auditLogService.logAction(
                    user.getId(),
                    user.getEmail(),
                    "LOGIN",
                    "User",
                    user.getId(),
                    "Login efetuado com sucesso pelo usuário: " + user.getEmail(),
                    "SUCCESS"
            );

            return new TokenResponse(jwtToken, refreshToken);
        } catch (Exception e) {
            // Registo de auditoria de falha
            Long userId = null;
            try {
                userId = userRepository.findByEmail(request.email()).map(User::getId).orElse(null);
            } catch (Exception ignored) {}

            auditLogService.logAction(
                    userId,
                    request.email(),
                    "LOGIN",
                    "User",
                    userId,
                    "Falha na tentativa de login com o e-mail: " + request.email(),
                    "FAILURE",
                    e.getMessage()
            );
            throw e;
        }
    }

    public TokenResponse refresh(String refreshToken) {
        String userEmail;
        try {
            userEmail = jwtService.extractUsername(refreshToken);
        } catch (Exception e) {
            throw new UnauthorizedException("Refresh token inválido");
        }

        if (userEmail != null) {
            User user = userRepository.findByEmail(userEmail)
                    .orElseThrow(() -> new UnauthorizedException("Usuário não encontrado"));

            if (!user.getActive()) {
                throw new UnauthorizedException("Sua conta está inativa");
            }

            if (jwtService.isTokenValid(refreshToken, user)) {
                String accessToken = jwtService.generateAccessToken(user);
                return new TokenResponse(accessToken, refreshToken);
            }
        }
        
        throw new UnauthorizedException("Refresh token expirado ou inválido");
    }
}
