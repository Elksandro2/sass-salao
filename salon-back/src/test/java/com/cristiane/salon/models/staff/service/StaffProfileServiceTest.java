package com.cristiane.salon.models.staff.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ConflictException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.staff.dto.StaffProfileRequest;
import com.cristiane.salon.models.staff.dto.StaffProfileResponse;
import com.cristiane.salon.models.staff.entity.StaffProfile;
import com.cristiane.salon.models.staff.enums.BrazilianState;
import com.cristiane.salon.models.staff.enums.PixKeyType;
import com.cristiane.salon.models.staff.factory.StaffRoleStrategy;
import com.cristiane.salon.models.staff.factory.StaffRoleStrategyFactory;
import com.cristiane.salon.models.staff.repository.StaffProfileRepository;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.RoleRepository;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.security.crypto.PiiHashUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StaffProfileServiceTest {

    @Mock
    private StaffProfileRepository staffProfileRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private PiiHashUtil piiHashUtil;
    @Mock
    private StaffRoleStrategyFactory strategyFactory;
    @Mock
    private StaffRoleStrategy strategy;

    @InjectMocks
    private StaffProfileService staffProfileService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private StaffProfileRequest validFuncionariaRequest() {
        return new StaffProfileRequest(
                "Maria", "maria@example.com", "Senha@123", "FUNCIONARIA",
                "Maria Silva", null, "111.444.777-35", LocalDate.of(1990, 1, 1), null,
                "81999998888", "João", "81988887777",
                "50000-000", "Rua A", "10", null, "Boa Vista", "Recife", BrazilianState.PE,
                PixKeyType.EMAIL, "maria@example.com",
                LocalDate.now(), "observações",
                RemunerationType.SALARIO_FIXO, new BigDecimal("2000")
        );
    }

    private void mockAuthenticatedUser(String email) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(email);
        SecurityContext ctx = mock(SecurityContext.class);
        lenient().when(ctx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(ctx);
    }

    @Test
    void create_whenValidFuncionariaRequest_shouldPersistUserAndProfileAndDelegateToStrategy() {
        StaffProfileRequest request = validFuncionariaRequest();
        mockAuthenticatedUser("admin@example.com");

        when(strategyFactory.resolve("FUNCIONARIA")).thenReturn(strategy);
        when(userRepository.findByEmail("maria@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("admin@example.com"))
                .thenReturn(Optional.of(adminUser()));
        when(piiHashUtil.hash("11144477735")).thenReturn("hash-abc");
        when(staffProfileRepository.existsByCpfHash("hash-abc")).thenReturn(false);
        Role role = new Role(3L, "FUNCIONARIA", null);
        when(roleRepository.findByName("FUNCIONARIA")).thenReturn(Optional.of(role));
        when(passwordEncoder.encode("Senha@123")).thenReturn("encoded-password");

        User savedUser = new User();
        savedUser.setId(10L);
        savedUser.setName("Maria");
        savedUser.setEmail("maria@example.com");
        savedUser.setRole(role);
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        when(staffProfileRepository.save(any(StaffProfile.class))).thenAnswer(inv -> {
            StaffProfile p = inv.getArgument(0);
            p.setId(100L);
            return p;
        });

        StaffProfileResponse response = staffProfileService.create(request);

        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.fullName()).isEqualTo("Maria Silva");
        assertThat(response.cpfMasked()).isEqualTo("***.***.777-35");

        verify(strategy).validate(request);
        verify(strategy).onStaffCreated(savedUser, request);

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getPassword()).isEqualTo("encoded-password");

        ArgumentCaptor<StaffProfile> profileCaptor = ArgumentCaptor.forClass(StaffProfile.class);
        verify(staffProfileRepository).save(profileCaptor.capture());
        assertThat(profileCaptor.getValue().getCpf()).isEqualTo("11144477735");
        assertThat(profileCaptor.getValue().getCpfHash()).isEqualTo("hash-abc");
    }

    @Test
    void create_whenEmailAlreadyInUse_shouldThrowConflictException() {
        StaffProfileRequest request = validFuncionariaRequest();
        when(strategyFactory.resolve("FUNCIONARIA")).thenReturn(strategy);
        when(userRepository.findByEmail("maria@example.com")).thenReturn(Optional.of(new User()));

        assertThatThrownBy(() -> staffProfileService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Email já está em uso");

        verify(staffProfileRepository, never()).save(any());
    }

    @Test
    void create_whenCpfHashAlreadyExists_shouldThrowConflictExceptionWithoutRevealingWhichCpf() {
        StaffProfileRequest request = validFuncionariaRequest();
        when(strategyFactory.resolve("FUNCIONARIA")).thenReturn(strategy);
        when(userRepository.findByEmail("maria@example.com")).thenReturn(Optional.empty());
        when(piiHashUtil.hash("11144477735")).thenReturn("hash-abc");
        when(staffProfileRepository.existsByCpfHash("hash-abc")).thenReturn(true);

        assertThatThrownBy(() -> staffProfileService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("Já existe um cadastro de equipe com este CPF");

        verify(userRepository, never()).save(any());
    }

    @Test
    void create_whenUnsupportedRole_shouldPropagateBadRequestFromFactory() {
        StaffProfileRequest request = new StaffProfileRequest(
                "Ana", "ana@example.com", "Senha@123", "ADMIN",
                "Ana Souza", null, "111.444.777-35", LocalDate.of(1990, 1, 1), null,
                "81999998888", null, null,
                "50000-000", "Rua A", "10", null, "Boa Vista", "Recife", BrazilianState.PE,
                null, null, LocalDate.now(), null,
                null, null
        );
        when(strategyFactory.resolve("ADMIN")).thenThrow(new BadRequestException("papel não suportado"));

        assertThatThrownBy(() -> staffProfileService.create(request))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userRepository);
    }

    @Test
    void create_whenStrategyValidationFails_shouldNotPersistAnything() {
        StaffProfileRequest request = validFuncionariaRequest();
        when(strategyFactory.resolve("FUNCIONARIA")).thenReturn(strategy);
        doThrow(new BadRequestException("remuneração obrigatória")).when(strategy).validate(request);

        assertThatThrownBy(() -> staffProfileService.create(request))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(userRepository, staffProfileRepository);
    }

    @Test
    void findById_whenNotFound_shouldThrowResourceNotFoundException() {
        when(staffProfileRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> staffProfileService.findById(1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void staffProfileResponse_shouldNeverExposeRawCpfOrPixKeyAsAnAccessor() {
        // Garantia estrutural: nem por engano alguém adiciona um campo cru ao DTO de resposta.
        // Os únicos acessores relacionados a estes dados devem ser as versões mascaradas.
        Method[] methods = StaffProfileResponse.class.getDeclaredMethods();
        boolean hasRawCpfAccessor = Arrays.stream(methods).anyMatch(m -> m.getName().equals("cpf"));
        boolean hasRawPixKeyAccessor = Arrays.stream(methods).anyMatch(m -> m.getName().equals("pixKey"));

        assertThat(hasRawCpfAccessor).isFalse();
        assertThat(hasRawPixKeyAccessor).isFalse();
        assertThat(Arrays.stream(methods).map(Method::getName))
                .contains("cpfMasked", "pixKeyMasked")
                .doesNotContain("cpf", "pixKey");
    }

    private User adminUser() {
        User admin = new User();
        admin.setId(1L);
        admin.setEmail("admin@example.com");
        return admin;
    }
}
