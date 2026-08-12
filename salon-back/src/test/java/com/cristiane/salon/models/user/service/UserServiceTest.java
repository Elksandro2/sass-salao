package com.cristiane.salon.models.user.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ConflictException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.exception.UnauthorizedException;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.user.dto.UserCreateRequest;
import com.cristiane.salon.models.user.dto.UserResponse;
import com.cristiane.salon.models.user.dto.UserUpdateRequest;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.RoleRepository;
import com.cristiane.salon.models.user.repository.UserRepository;
import com.cristiane.salon.models.user.dto.ClientFilter;
import com.cristiane.salon.models.user.dto.UserFilter;
import com.cristiane.salon.models.user.dto.ClientDetailsResponse;
import com.cristiane.salon.models.user.dto.UserCpfInfoResponse;
import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.repository.AppointmentRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.time.Instant;
import java.time.ZoneOffset;
import com.cristiane.salon.config.SalonClock;
import java.time.ZoneId;
import org.mockito.Spy;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AppointmentRepository appointmentRepository;

    // SalonClock real, não mock: os testes dependem do "hoje"/"agora" de verdade no fuso
    // do salão, e um mock devolveria null silenciosamente.
    @Spy
    private SalonClock salonClock = new SalonClock(ZoneId.of("America/Recife"));

    @InjectMocks
    private UserService userService;

    private User activeUser;
    private User inactiveUser;
    private Role clientRole;
    private Role staffRole;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void mockAuthenticatedUser(User user) {
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getName()).thenReturn(user.getEmail());
        SecurityContext secCtx = mock(SecurityContext.class);
        lenient().when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        lenient().when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }


    @BeforeEach
    void setUp() {
        clientRole = new Role(1L, "CLIENTE", null);

        staffRole = new Role(2L, "FUNCIONARIA", null);

        activeUser = new User();
        activeUser.setId(10L);
        activeUser.setName("Maria Silva");
        activeUser.setEmail("maria@example.com");
        activeUser.setPassword("encodedPassword");
        activeUser.setPhone("81999999999");
        activeUser.setActive(true);
        activeUser.setRole(clientRole);
        activeUser.setCreatedAt(Instant.now());

        inactiveUser = new User();
        inactiveUser.setId(11L);
        inactiveUser.setName("Joana Inativa");
        inactiveUser.setEmail("joana@example.com");
        inactiveUser.setPassword("encodedPassword");
        inactiveUser.setPhone("81888888888");
        inactiveUser.setActive(false);
        inactiveUser.setRole(clientRole);
        inactiveUser.setCreatedAt(Instant.now());
    }

    private void attachService(Appointment appointment, String serviceName) {
        com.cristiane.salon.models.service.entity.SalonService svc =
                new com.cristiane.salon.models.service.entity.SalonService();
        svc.setName(serviceName);
        com.cristiane.salon.models.appointment.entity.AppointmentServiceItem item =
                new com.cristiane.salon.models.appointment.entity.AppointmentServiceItem();
        item.setAppointment(appointment);
        item.setSalonService(svc);
        appointment.getServices().add(item);
    }

    @Test
    void findAll_whenIncludeInactiveIsTrue_shouldReturnAllUsers() {
        // Arrange
        when(userRepository.findAll()).thenReturn(List.of(activeUser, inactiveUser));

        // Act
        List<UserResponse> result = userService.findAll(true);

        // Assert
        assertThat(result).hasSize(2);
        assertThat(result.get(0).email()).isEqualTo("maria@example.com");
        assertThat(result.get(1).email()).isEqualTo("joana@example.com");
        verify(userRepository).findAll();
        verify(userRepository, never()).findByActiveTrue();
    }

    @Test
    void findAll_whenIncludeInactiveIsFalse_shouldReturnActiveUsersOnly() {
        // Arrange
        when(userRepository.findByActiveTrue()).thenReturn(List.of(activeUser));

        // Act
        List<UserResponse> result = userService.findAll(false);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("maria@example.com");
        verify(userRepository).findByActiveTrue();
        verify(userRepository, never()).findAll();
    }

    @Test
    void findAll_whenIncludeInactiveIsNull_shouldReturnActiveUsersOnly() {
        // Arrange
        when(userRepository.findByActiveTrue()).thenReturn(List.of(activeUser));

        // Act
        List<UserResponse> result = userService.findAll(null);

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).email()).isEqualTo("maria@example.com");
        verify(userRepository).findByActiveTrue();
        verify(userRepository, never()).findAll();
    }

    @Test
    void findById_whenUserExists_shouldReturnUserResponse() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));

        // Act
        UserResponse result = userService.findById(10L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.email()).isEqualTo("maria@example.com");
        verify(userRepository).findById(10L);
    }

    @Test
    void findById_whenUserDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void create_whenEmailAlreadyInUse_shouldThrowConflictException() {
        // Arrange
        UserCreateRequest request = new UserCreateRequest("New User", "maria@example.com", "password", "123", true, 1L);
        when(userRepository.findByEmail("maria@example.com")).thenReturn(Optional.of(activeUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email já está em uso");
    }

    @Test
    void create_whenRoleNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        UserCreateRequest request = new UserCreateRequest("New User", "new@example.com", "password", "123", true, 9L);
        when(userRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findById(9L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Role não encontrada");
    }

    @Test
    void create_whenClientRoleAndActiveTrue_shouldCreateUserWithoutEmployee() {
        // Arrange
        UserCreateRequest request = new UserCreateRequest("New Client", "client@example.com", "password", "123", true, 1L);
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode("password")).thenReturn("encoded_pass");

        User savedUser = new User();
        savedUser.setId(12L);
        savedUser.setName(request.name());
        savedUser.setEmail(request.email());
        savedUser.setPassword("encoded_pass");
        savedUser.setPhone(request.phone());
        savedUser.setRole(clientRole);
        savedUser.setActive(true);
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        UserResponse result = userService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(12L);
        assertThat(result.active()).isTrue();
        assertThat(result.role()).isEqualTo("CLIENTE");

        verify(userRepository).save(any(User.class));
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void create_whenActiveFieldIsNull_shouldDefaultToTrue() {
        // Arrange
        UserCreateRequest request = new UserCreateRequest("New Client", "client@example.com", "password", "123", null, 1L);
        when(userRepository.findByEmail("client@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findById(1L)).thenReturn(Optional.of(clientRole));
        when(passwordEncoder.encode("password")).thenReturn("encoded_pass");

        User savedUser = new User();
        savedUser.setId(12L);
        savedUser.setName(request.name());
        savedUser.setEmail(request.email());
        savedUser.setPassword("encoded_pass");
        savedUser.setPhone(request.phone());
        savedUser.setRole(clientRole);
        savedUser.setActive(true);
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        UserResponse result = userService.create(request);

        // Assert
        assertThat(result.active()).isTrue();
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        assertThat(userCaptor.getValue().getActive()).isTrue();
    }

    @Test
    void create_whenStaffRole_shouldCreateUserAndAutoCreateEmployee() {
        // Arrange
        UserCreateRequest request = new UserCreateRequest("New Staff", "staff@example.com", "password", "123", true, 2L);
        when(userRepository.findByEmail("staff@example.com")).thenReturn(Optional.empty());
        when(roleRepository.findById(2L)).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode("password")).thenReturn("encoded_pass");

        User savedUser = new User();
        savedUser.setId(13L);
        savedUser.setName(request.name());
        savedUser.setEmail(request.email());
        savedUser.setPassword("encoded_pass");
        savedUser.setPhone(request.phone());
        savedUser.setRole(staffRole);
        savedUser.setActive(true);
        savedUser.setCreatedAt(Instant.now());

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        UserResponse result = userService.create(request);

        // Assert
        assertThat(result.role()).isEqualTo("FUNCIONARIA");
        verify(userRepository).save(any(User.class));
        
        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        Employee savedEmployee = employeeCaptor.getValue();
        assertThat(savedEmployee.getUser()).isEqualTo(savedUser);
    }

    @Test
    void update_whenUserNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        UserUpdateRequest request = new UserUpdateRequest("Updated Name", "email@example.com", "pass", "123", null, false, 1L);
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void update_whenEmailChangedAndAlreadyInUse_shouldThrowConflictException() {
        // Arrange
        UserUpdateRequest request = new UserUpdateRequest(null, "other@example.com", null, null, null, null, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.findByEmail("other@example.com")).thenReturn(Optional.of(inactiveUser));

        // Act & Assert
        assertThatThrownBy(() -> userService.update(10L, request))
                .isInstanceOf(ConflictException.class)
                .hasMessage("Email já está em uso por outro usuário");
    }

    @Test
    void update_whenRoleChangedAndRoleNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, null, null, 99L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.update(10L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Role não encontrada");
    }

    @Test
    void update_withAllFieldsAndChangeRoleToStaff_whenEmployeeDoesNotExist_shouldCreateEmployee() {
        // Arrange
        UserUpdateRequest request = new UserUpdateRequest("Updated Name", "maria@example.com", "new_password", "8177777777", null, false, 2L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(staffRole));
        when(passwordEncoder.encode("new_password")).thenReturn("new_encoded_pass");
        
        // Mock saved state
        User updatedUser = new User();
        updatedUser.setId(10L);
        updatedUser.setName("Updated Name");
        updatedUser.setEmail("maria@example.com");
        updatedUser.setPassword("new_encoded_pass");
        updatedUser.setPhone("8177777777");
        updatedUser.setActive(false);
        updatedUser.setRole(staffRole); // Updated to staff

        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.empty());

        // Act
        UserResponse result = userService.update(10L, request);

        // Assert
        assertThat(result.name()).isEqualTo("Updated Name");
        assertThat(result.phone()).isEqualTo("8177777777");
        assertThat(result.active()).isFalse();
        assertThat(result.role()).isEqualTo("FUNCIONARIA");

        verify(passwordEncoder).encode("new_password");
        verify(userRepository).save(activeUser);
        
        ArgumentCaptor<Employee> employeeCaptor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(employeeCaptor.capture());
        assertThat(employeeCaptor.getValue().getUser()).isEqualTo(updatedUser);
    }

    @Test
    void update_withChangeRoleToStaff_whenEmployeeAlreadyExists_shouldNotDuplicateEmployee() {
        // Arrange
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, null, null, 2L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(staffRole));

        User updatedUser = new User();
        updatedUser.setId(10L);
        updatedUser.setName("Maria Silva");
        updatedUser.setEmail("maria@example.com");
        updatedUser.setPassword("encodedPassword");
        updatedUser.setRole(staffRole); // Updated to staff
        updatedUser.setActive(true);

        when(userRepository.save(any(User.class))).thenReturn(updatedUser);
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.of(new Employee()));

        // Act
        UserResponse result = userService.update(10L, request);

        // Assert
        assertThat(result.role()).isEqualTo("FUNCIONARIA");
        verify(userRepository).save(activeUser);
        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void update_whenPasswordIsBlankOrNull_shouldNotEncodePassword() {
        // Arrange
        UserUpdateRequest request = new UserUpdateRequest(null, null, "  ", null, null, null, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        // Act
        userService.update(10L, request);

        // Assert
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    void delete_whenUserExists_shouldSetActiveToFalse() {
        // Arrange
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        // Act
        userService.delete(10L);

        // Assert
        assertThat(activeUser.getActive()).isFalse();
        verify(userRepository).save(activeUser);
    }

    @Test
    void delete_whenUserNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void restore_whenUserExists_shouldSetActiveToTrue() {
        // Arrange
        when(userRepository.findById(11L)).thenReturn(Optional.of(inactiveUser));
        
        User savedUser = new User();
        savedUser.setId(11L);
        savedUser.setEmail("joana@example.com");
        savedUser.setActive(true);
        savedUser.setRole(clientRole);

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        // Act
        UserResponse result = userService.restore(11L);

        // Assert
        assertThat(inactiveUser.getActive()).isTrue();
        assertThat(result.active()).isTrue();
        verify(userRepository).save(inactiveUser);
    }

    @Test
    void restore_whenUserNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.restore(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    // --- updateMyCpf tests ---

    @Test
    void updateMyCpf_whenSuccess_shouldSaveCpfForAuthenticatedUser() {
        // Arrange
        mockAuthenticatedUser(activeUser);
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        // Act
        UserResponse result = userService.updateMyCpf("09123456752");

        // Assert
        assertThat(activeUser.getCpf()).isEqualTo("09123456752");
        assertThat(result).isNotNull();
        verify(userRepository).save(activeUser);
    }

    @Test
    void updateMyCpf_whenSameUserAlreadyHasCpf_shouldUpdateSuccessfully() {
        // Arrange — user 10 already owns this CPF
        activeUser.setCpf("09123456752");
        mockAuthenticatedUser(activeUser);
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        // Act
        UserResponse result = userService.updateMyCpf("09123456752");

        // Assert — should NOT throw
        assertThat(result).isNotNull();
        verify(userRepository).save(activeUser);
    }

    @Test
    void updateMyCpf_whenUserNotAuthenticated_shouldThrowUnauthorizedException() {
        // Arrange — configure SecurityContext with unknown email
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("unknown@ghost.com");
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        when(userRepository.findByEmail("unknown@ghost.com")).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.updateMyCpf("09123456752"))
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Usuário não autenticado");
    }

    @Test
    void updateMyCpf_whenCpfIsInvalid_shouldThrowBadRequestException() {
        // Arrange
        mockAuthenticatedUser(activeUser);

        // Act & Assert
        assertThatThrownBy(() -> userService.updateMyCpf("11111111111"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CPF inválido. Por favor, insira um CPF válido.");
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllClients_shouldCallUserRepositoryWithSpecification() {
        // Arrange
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<User> page = new org.springframework.data.domain.PageImpl<>(List.of(activeUser));
        when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable))).thenReturn(page);

        ClientFilter filter = new ClientFilter("Maria", null, null, null, true);

        // Act
        org.springframework.data.domain.Page<UserResponse> result = userService.findAllClients(filter, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Maria Silva");
        verify(userRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable));
    }

    @Test
    @SuppressWarnings("unchecked")
    void findAllUsers_shouldCallUserRepositoryWithSpecification() {
        // Arrange
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        User staff = new User();
        staff.setId(14L);
        staff.setName("Staff Name");
        staff.setEmail("staff@example.com");
        staff.setRole(staffRole);
        staff.setActive(true);

        org.springframework.data.domain.Page<User> page = new org.springframework.data.domain.PageImpl<>(List.of(staff));
        when(userRepository.findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable))).thenReturn(page);

        UserFilter filter = new UserFilter("Staff", null, null, true, 2L);

        // Act
        org.springframework.data.domain.Page<UserResponse> result = userService.findAllUsers(filter, pageable);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).name()).isEqualTo("Staff Name");
        verify(userRepository).findAll(any(org.springframework.data.jpa.domain.Specification.class), eq(pageable));
    }

    @Test
    void findClientDetailsById_whenUserDoesNotExist_shouldThrowResourceNotFoundException() {
        // Arrange
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> userService.findClientDetailsById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Cliente não encontrado");
    }

    @Test
    void findClientDetailsById_whenUserIsNotClient_shouldThrowBadRequestException() {
        // Arrange
        User staff = new User();
        staff.setId(15L);
        staff.setName("Jane Staff");
        staff.setRole(staffRole); // FUNCIONARIA
        when(userRepository.findById(15L)).thenReturn(Optional.of(staff));

        // Act & Assert
        assertThatThrownBy(() -> userService.findClientDetailsById(15L))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O usuário informado não é um cliente");
    }

    @Test
    void findClientDetailsById_whenClientExists_shouldReturnDetailsWithSortedAppointments() {
        // Arrange
        activeUser.setRole(clientRole); // CLIENTE
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(appointmentRepository.countByClientId(10L)).thenReturn(2L);
        
        LocalDateTime lastDate = LocalDateTime.of(2026, 6, 25, 14, 0);
        when(appointmentRepository.findLastAppointmentDateByClientId(10L)).thenReturn(lastDate);

        Appointment app1 = new Appointment();
        app1.setId(101L);
        app1.setClient(activeUser);
        app1.setEmployee(new Employee());
        app1.getEmployee().setUser(activeUser);
        attachService(app1, "Service A");
        app1.setScheduledAt(LocalDateTime.of(2026, 6, 24, 10, 0));
        app1.setStatus(com.cristiane.salon.models.appointment.enums.AppointmentStatus.CONFIRMED);

        Appointment app2 = new Appointment();
        app2.setId(102L);
        app2.setClient(activeUser);
        app2.setEmployee(new Employee());
        app2.getEmployee().setUser(activeUser);
        attachService(app2, "Service B");
        app2.setScheduledAt(LocalDateTime.of(2026, 6, 25, 14, 0)); // Later date
        app2.setStatus(com.cristiane.salon.models.appointment.enums.AppointmentStatus.CONFIRMED);

        when(appointmentRepository.findByClientId(10L)).thenReturn(List.of(app1, app2));

        // Act
        ClientDetailsResponse result = userService.findClientDetailsById(10L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(10L);
        assertThat(result.totalAppointments()).isEqualTo(2L);
        assertThat(result.lastAppointmentDate()).isEqualTo(lastDate);
        assertThat(result.appointments()).hasSize(2);
        // Sorted desc by scheduledAt: app2 should be first
        assertThat(result.appointments().get(0).id()).isEqualTo(102L);
        assertThat(result.appointments().get(1).id()).isEqualTo(101L);
    }

    @Test
    void findClientDetailsById_whenAppointmentsHaveNullScheduledAt_shouldSortByCreatedAt() {
        // Arrange
        activeUser.setRole(clientRole);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(appointmentRepository.countByClientId(10L)).thenReturn(3L);
        when(appointmentRepository.findLastAppointmentDateByClientId(10L)).thenReturn(null);

        Appointment app1 = new Appointment();
        app1.setId(101L);
        app1.setClient(activeUser);
        app1.setEmployee(new Employee());
        app1.getEmployee().setUser(activeUser);
        attachService(app1, "Service A");
        app1.setScheduledAt(null);
        app1.setCreatedAt(LocalDateTime.of(2026, 6, 24, 10, 0).toInstant(ZoneOffset.UTC));
        app1.setStatus(com.cristiane.salon.models.appointment.enums.AppointmentStatus.PENDING);

        Appointment app2 = new Appointment();
        app2.setId(102L);
        app2.setClient(activeUser);
        app2.setEmployee(new Employee());
        app2.getEmployee().setUser(activeUser);
        attachService(app2, "Service B");
        app2.setScheduledAt(null);
        app2.setCreatedAt(LocalDateTime.of(2026, 6, 25, 14, 0).toInstant(ZoneOffset.UTC)); // Later createdAt
        app2.setStatus(com.cristiane.salon.models.appointment.enums.AppointmentStatus.PENDING);

        Appointment app3 = new Appointment();
        app3.setId(103L);
        app3.setClient(activeUser);
        app3.setEmployee(new Employee());
        app3.getEmployee().setUser(activeUser);
        attachService(app3, "Service C");
        app3.setScheduledAt(null);
        app3.setCreatedAt(null); // Both scheduledAt and createdAt null
        app3.setStatus(com.cristiane.salon.models.appointment.enums.AppointmentStatus.PENDING);

        when(appointmentRepository.findByClientId(10L)).thenReturn(List.of(app1, app2, app3));

        // Act
        ClientDetailsResponse result = userService.findClientDetailsById(10L);

        // Assert
        assertThat(result.appointments()).hasSize(3);
        assertThat(result.appointments().get(0).id()).isEqualTo(102L);
        assertThat(result.appointments().get(1).id()).isEqualTo(101L);
        assertThat(result.appointments().get(2).id()).isEqualTo(103L);
    }

    @Test
    void update_whenEmailChangedSuccessfully_shouldUpdateEmail() {
        UserUpdateRequest request = new UserUpdateRequest(null, "newemail@example.com", null, null, null, null, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.findByEmail("newemail@example.com")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        UserResponse result = userService.update(10L, request);

        assertThat(activeUser.getEmail()).isEqualTo("newemail@example.com");
        verify(userRepository).save(activeUser);
    }

    @Test
    void update_whenCpfIsValid_shouldUpdateCpf() {
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, "123.456.789-09", null, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        UserResponse result = userService.update(10L, request);

        assertThat(activeUser.getCpf()).isEqualTo("12345678909");
        verify(userRepository).save(activeUser);
    }

    @Test
    void update_whenCpfIsInvalid_shouldThrowBadRequestException() {
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, "12345", null, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> userService.update(10L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CPF inválido. Por favor, insira um CPF válido.");
        verify(userRepository, never()).save(any());
    }

    @Test
    void update_whenCpfIsNullAndBlank_shouldNotUpdateCpf() {
        // CPF null
        UserUpdateRequest requestNull = new UserUpdateRequest(null, null, null, null, null, null, null);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        userService.update(10L, requestNull);
        assertThat(activeUser.getCpf()).isNull();

        // CPF blank
        UserUpdateRequest requestBlank = new UserUpdateRequest(null, null, null, null, "   ", null, null);
        userService.update(10L, requestBlank);
        assertThat(activeUser.getCpf()).isNull();
    }

    @Test
    void update_whenClientRoleSaved_shouldNotCreateEmployee() {
        UserUpdateRequest request = new UserUpdateRequest(null, null, null, null, null, null, 1L);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(roleRepository.findById(1L)).thenReturn(Optional.of(clientRole));
        when(userRepository.save(any(User.class))).thenReturn(activeUser);

        userService.update(10L, request);

        verify(employeeRepository, never()).save(any());
    }

    @Test
    void updateMyCpf_whenCpfIsNull_shouldThrowBadRequestException() {
        mockAuthenticatedUser(activeUser);

        assertThatThrownBy(() -> userService.updateMyCpf(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CPF é obrigatório.");
    }

    @Test
    void updateMyCpf_whenCpfIsBlank_shouldThrowBadRequestException() {
        mockAuthenticatedUser(activeUser);

        assertThatThrownBy(() -> userService.updateMyCpf("  "))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("CPF é obrigatório.");
    }

    @Test
    void getMyCpfInfo_whenUserNotAuthenticated_shouldThrowUnauthorizedException() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("unknown@ghost.com");
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);
        when(userRepository.findByEmail("unknown@ghost.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyCpfInfo())
                .isInstanceOf(UnauthorizedException.class)
                .hasMessage("Usuário não autenticado");
    }

    @Test
    void getMyCpfInfo_whenUserHasNoCpf_shouldReturnHasSavedCpfFalse() {
        activeUser.setCpf(null);
        mockAuthenticatedUser(activeUser);

        UserCpfInfoResponse info = userService.getMyCpfInfo();

        assertThat(info.hasSavedCpf()).isFalse();
        assertThat(info.cpfMasked()).isEmpty();
    }

    @Test
    void getMyCpfInfo_whenUserHasValid11DigitCpf_shouldReturnCpfMasked() {
        activeUser.setCpf("12345678909");
        mockAuthenticatedUser(activeUser);

        UserCpfInfoResponse info = userService.getMyCpfInfo();

        assertThat(info.hasSavedCpf()).isTrue();
        assertThat(info.cpfMasked()).isEqualTo("***.***.789-");
    }

    @Test
    void getMyCpfInfo_whenUserHasNon11DigitCpf_shouldReturnUnchangedCpf() {
        activeUser.setCpf("12345");
        mockAuthenticatedUser(activeUser);

        UserCpfInfoResponse info = userService.getMyCpfInfo();

        assertThat(info.hasSavedCpf()).isTrue();
        assertThat(info.cpfMasked()).isEqualTo("12345");
    }

    @Test
    void findClientDetailsById_allAppointmentComparatorBranches() {
        activeUser.setRole(clientRole);
        when(userRepository.findById(10L)).thenReturn(Optional.of(activeUser));
        when(appointmentRepository.countByClientId(10L)).thenReturn(5L);
        when(appointmentRepository.findLastAppointmentDateByClientId(10L)).thenReturn(null);

        Appointment appNull1 = new Appointment();
        appNull1.setId(201L);
        appNull1.setClient(activeUser);
        appNull1.setEmployee(new Employee());
        appNull1.getEmployee().setUser(activeUser);
        attachService(appNull1, "Service");
        appNull1.setScheduledAt(null);
        appNull1.setCreatedAt(null);

        Appointment appNull2 = new Appointment();
        appNull2.setId(202L);
        appNull2.setClient(activeUser);
        appNull2.setEmployee(new Employee());
        appNull2.getEmployee().setUser(activeUser);
        attachService(appNull2, "Service");
        appNull2.setScheduledAt(null);
        appNull2.setCreatedAt(null);

        Appointment appDate1 = new Appointment();
        appDate1.setId(203L);
        appDate1.setClient(activeUser);
        appDate1.setEmployee(new Employee());
        appDate1.getEmployee().setUser(activeUser);
        attachService(appDate1, "Service");
        appDate1.setScheduledAt(LocalDateTime.of(2026, 6, 24, 10, 0));

        Appointment appDate2 = new Appointment();
        appDate2.setId(204L);
        appDate2.setClient(activeUser);
        appDate2.setEmployee(new Employee());
        appDate2.getEmployee().setUser(activeUser);
        attachService(appDate2, "Service");
        appDate2.setScheduledAt(LocalDateTime.of(2026, 6, 25, 10, 0));

        Appointment appDate3 = new Appointment();
        appDate3.setId(205L);
        appDate3.setClient(activeUser);
        appDate3.setEmployee(new Employee());
        appDate3.getEmployee().setUser(activeUser);
        attachService(appDate3, "Service");
        appDate3.setScheduledAt(LocalDateTime.of(2026, 6, 24, 10, 0));

        when(appointmentRepository.findByClientId(10L)).thenReturn(List.of(appNull1, appNull2, appDate1, appDate2, appDate3));

        ClientDetailsResponse result = userService.findClientDetailsById(10L);
        assertThat(result.appointments()).hasSize(5);
        assertThat(result.appointments().get(0).id()).isEqualTo(204L);
        assertThat(result.appointments().get(1).id()).isIn(203L, 205L);
        assertThat(result.appointments().get(2).id()).isIn(203L, 205L);
        assertThat(result.appointments().get(3).id()).isIn(201L, 202L);
        assertThat(result.appointments().get(4).id()).isIn(201L, 202L);
    }

    // ---- getMyProfile ----

    @Test
    void getMyProfile_whenAuthenticated_shouldReturnProfileWithPermissions() {
        com.cristiane.salon.models.user.entity.Permission perm1 =
                new com.cristiane.salon.models.user.entity.Permission(1L, "Listar Usuários", "/v1/users", "GET", "Usuário");
        com.cristiane.salon.models.user.entity.Permission perm2 =
                new com.cristiane.salon.models.user.entity.Permission(2L, "Criar Usuário", "/v1/users", "POST", "Usuário");

        Role roleWithPerms = new Role(3L, "GERENTE_DE_ATENDIMENTO", new java.util.HashSet<>(java.util.Set.of(perm1, perm2)));
        activeUser.setRole(roleWithPerms);

        mockAuthenticatedUser(activeUser);

        com.cristiane.salon.models.user.dto.UserProfileResponse profile = userService.getMyProfile();

        assertThat(profile).isNotNull();
        assertThat(profile.email()).isEqualTo("maria@example.com");
        assertThat(profile.role()).isEqualTo("GERENTE_DE_ATENDIMENTO");
        assertThat(profile.permissions()).hasSize(2);
        assertThat(profile.permissions()).containsExactlyInAnyOrder("GET:/v1/users", "POST:/v1/users");
    }

    @Test
    void getMyProfile_whenRoleHasNoPermissions_shouldReturnEmptyPermissions() {
        clientRole.setPermissions(java.util.Collections.emptySet());
        activeUser.setRole(clientRole);
        mockAuthenticatedUser(activeUser);

        com.cristiane.salon.models.user.dto.UserProfileResponse profile = userService.getMyProfile();

        assertThat(profile.permissions()).isEmpty();
    }

    @Test
    void getMyProfile_whenUserNotFound_shouldThrowUnauthorizedException() {
        Authentication auth = mock(Authentication.class);
        when(auth.getName()).thenReturn("unknown@example.com");
        SecurityContext secCtx = mock(SecurityContext.class);
        when(secCtx.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(secCtx);

        when(userRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.getMyProfile())
                .isInstanceOf(com.cristiane.salon.exception.UnauthorizedException.class);
    }
}
