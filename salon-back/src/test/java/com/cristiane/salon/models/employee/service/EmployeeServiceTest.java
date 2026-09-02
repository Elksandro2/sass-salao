package com.cristiane.salon.models.employee.service;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.exception.ResourceNotFoundException;
import com.cristiane.salon.models.employee.dto.EmployeeBookingResponse;
import com.cristiane.salon.models.employee.dto.EmployeeFilter;
import com.cristiane.salon.models.employee.dto.EmployeeRequest;
import com.cristiane.salon.models.employee.dto.EmployeeResponse;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.user.entity.Role;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.models.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private EmployeeService employeeService;

    private User staffUser;
    private User clientUser;
    private Employee employee;

    @BeforeEach
    void setUp() {
        staffUser = new User();
        staffUser.setId(10L);
        staffUser.setName("Bruna");
        staffUser.setEmail("bruna@example.com");
        staffUser.setRole(new Role(1L, "FUNCIONARIA", null));

        clientUser = new User();
        clientUser.setId(11L);
        clientUser.setName("Joana");
        clientUser.setEmail("joana@example.com");
        clientUser.setRole(new Role(2L, "CLIENTE", null));

        employee = new Employee();
        employee.setId(1L);
        employee.setUser(staffUser);
    }

    @Test
    void findAll_shouldReturnPageFromRepository() {
        // Arrange
        Pageable pageable = PageRequest.of(0, 10);
        Page<Employee> page = new PageImpl<>(List.of(employee));
        when(employeeRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);

        // Act
        Page<EmployeeResponse> result = employeeService.findAll(new EmployeeFilter(null, null), pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(1L);
        verify(employeeRepository).findAll(any(Specification.class), eq(pageable));
    }

    @Test
    void findAllForBooking_shouldReturnBookingList() {
        // Arrange
        when(employeeRepository.findAllActiveForBooking()).thenReturn(List.of(employee));

        // Act
        List<EmployeeBookingResponse> result = employeeService.findAllForBooking();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo(1L);
    }

    @Test
    void findById_whenFound_shouldReturnEmployee() {
        // Arrange
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));

        // Act
        EmployeeResponse result = employeeService.findById(1L);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    void findById_whenNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> employeeService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Funcionária não encontrada");
    }

    // --- create tests ---

    @Test
    void create_whenUserAlreadyEmployee_shouldThrowBadRequestException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, null, null);
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.of(employee));

        // Act & Assert
        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Este usuário já é uma funcionária");
    }

    @Test
    void create_whenUserNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(99L, null, null);
        when(employeeRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void create_whenUserHasInvalidRole_shouldThrowBadRequestException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(11L, null, null);
        when(employeeRepository.findByUserId(11L)).thenReturn(Optional.empty());
        when(userRepository.findById(11L)).thenReturn(Optional.of(clientUser));

        // Act & Assert
        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O usuário não tem o papel adequado para ser funcionária");
    }

    @Test
    void create_whenRemunerationTypeIsNull_shouldSaveSuccessfully() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, null, null);
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> {
            Employee emp = invocation.getArgument(0);
            emp.setId(100L);
            return emp;
        });

        // Act
        EmployeeResponse result = employeeService.create(request);

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        verify(employeeRepository).save(any(Employee.class));
    }

    @Test
    void create_whenSalarioFixoAndRemunerationValueNegative_shouldThrowBadRequestException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, RemunerationType.SALARIO_FIXO, BigDecimal.valueOf(-10));
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));

        // Act & Assert
        assertThatThrownBy(() -> employeeService.create(request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("O valor de remuneração não pode ser negativo");
    }

    @Test
    void create_whenComissionadoSuccess_shouldSaveWithNullRemunerationValue() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, RemunerationType.COMISSIONADO, null);
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        EmployeeResponse result = employeeService.create(request);

        // Assert
        assertThat(result).isNotNull();
        verify(employeeRepository).save(argThat(emp ->
            emp.getRemunerationType() == RemunerationType.COMISSIONADO &&
            emp.getRemunerationValue() == null
        ));
    }

    @Test
    void create_whenSalarioFixoSuccess_shouldSaveWithCorrectDefaults() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, RemunerationType.SALARIO_FIXO, null);
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        EmployeeResponse result = employeeService.create(request);

        // Assert
        assertThat(result).isNotNull();
        verify(employeeRepository).save(argThat(emp ->
            emp.getRemunerationType() == RemunerationType.SALARIO_FIXO &&
            emp.getRemunerationValue().compareTo(BigDecimal.ZERO) == 0
        ));
    }

    @Test
    void create_whenFixoEComissionadoSuccess_shouldSaveCorrectly() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, RemunerationType.FIXO_E_COMISSIONADO, BigDecimal.valueOf(1500));
        when(employeeRepository.findByUserId(10L)).thenReturn(Optional.empty());
        when(userRepository.findById(10L)).thenReturn(Optional.of(staffUser));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        EmployeeResponse result = employeeService.create(request);

        // Assert
        assertThat(result).isNotNull();
        verify(employeeRepository).save(argThat(emp ->
            emp.getRemunerationType() == RemunerationType.FIXO_E_COMISSIONADO &&
            emp.getRemunerationValue().compareTo(BigDecimal.valueOf(1500)) == 0
        ));
    }

    // --- update tests ---

    @Test
    void update_whenNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(10L, null, null);
        when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> employeeService.update(99L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Funcionária não encontrada");
    }

    @Test
    void update_whenUserIdChangedAndAlreadyInUse_shouldThrowBadRequestException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(12L, null, null); // changing from 10L to 12L
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByUserId(12L)).thenReturn(Optional.of(new Employee()));

        // Act & Assert
        assertThatThrownBy(() -> employeeService.update(1L, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Este usuário já está vinculado a outra funcionária");
    }

    @Test
    void update_whenUserIdChangedAndUserNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        EmployeeRequest request = new EmployeeRequest(99L, null, null);
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByUserId(99L)).thenReturn(Optional.empty());
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThatThrownBy(() -> employeeService.update(1L, request))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Usuário não encontrado");
    }

    @Test
    void update_whenSuccessful_shouldSaveUpdatedFields() {
        // Arrange
        User newUser = new User();
        newUser.setId(12L);
        newUser.setName("Clara");
        newUser.setRole(new Role(1L, "FUNCIONARIA", null));

        EmployeeRequest request = new EmployeeRequest(12L, RemunerationType.SALARIO_FIXO, BigDecimal.valueOf(2000));
        
        when(employeeRepository.findById(1L)).thenReturn(Optional.of(employee));
        when(employeeRepository.findByUserId(12L)).thenReturn(Optional.empty());
        when(userRepository.findById(12L)).thenReturn(Optional.of(newUser));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        EmployeeResponse result = employeeService.update(1L, request);

        // Assert
        assertThat(result).isNotNull();
        verify(employeeRepository).save(argThat(emp ->
            emp.getUser().getId() == 12L &&
            emp.getRemunerationType() == RemunerationType.SALARIO_FIXO &&
            emp.getRemunerationValue().compareTo(BigDecimal.valueOf(2000)) == 0
        ));
    }

    // --- delete tests ---

    @Test
    void delete_whenNotFound_shouldThrowResourceNotFoundException() {
        // Arrange
        when(employeeRepository.existsById(99L)).thenReturn(false);

        // Act & Assert
        assertThatThrownBy(() -> employeeService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Funcionária não encontrada");
        verify(employeeRepository, never()).deleteById(any());
    }

    @Test
    void delete_whenFound_shouldDeleteById() {
        // Arrange
        when(employeeRepository.existsById(1L)).thenReturn(true);

        // Act
        employeeService.delete(1L);

        // Assert
        verify(employeeRepository).deleteById(1L);
    }

    // --- atuação como profissional do usuário logado (Meu Perfil do admin) ---

    private void authenticateAs(User user) {
        org.springframework.security.core.Authentication auth =
                mock(org.springframework.security.core.Authentication.class);
        when(auth.getName()).thenReturn(user.getEmail());
        org.springframework.security.core.context.SecurityContext ctx =
                mock(org.springframework.security.core.context.SecurityContext.class);
        when(ctx.getAuthentication()).thenReturn(auth);
        org.springframework.security.core.context.SecurityContextHolder.setContext(ctx);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
    }

    @org.junit.jupiter.api.AfterEach
    void clearSecurityContext() {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test
    void getMyActingProfile_whenNoEmployee_shouldReturnNotActing() {
        User admin = new User();
        admin.setId(50L);
        admin.setEmail("admin@salao.com");
        admin.setRole(new Role(9L, "ADMIN", null));
        authenticateAs(admin);
        when(employeeRepository.findByUserId(50L)).thenReturn(Optional.empty());

        var result = employeeService.getMyActingProfile();

        assertThat(result.hasProfile()).isFalse();
        assertThat(result.acting()).isFalse();
    }

    @Test
    void setMyActing_whenAdminActivatesFirstTime_shouldCreateBookableCommissionedEmployee() {
        User admin = new User();
        admin.setId(50L);
        admin.setEmail("admin@salao.com");
        admin.setRole(new Role(9L, "ADMIN", null));
        authenticateAs(admin);
        when(employeeRepository.findByUserId(50L)).thenReturn(Optional.empty());
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> {
            Employee e = inv.getArgument(0);
            e.setId(77L);
            return e;
        });

        var result = employeeService.setMyActing(true);

        assertThat(result.hasProfile()).isTrue();
        assertThat(result.acting()).isTrue();
        assertThat(result.remunerationType()).isEqualTo(RemunerationType.COMISSIONADO);
        assertThat(result.remunerationValue()).isNull();
    }

    @Test
    void setMyActing_whenAdminDeactivates_shouldKeepEmployeeButUnbook() {
        User admin = new User();
        admin.setId(50L);
        admin.setEmail("admin@salao.com");
        admin.setRole(new Role(9L, "ADMIN", null));
        authenticateAs(admin);

        Employee adminEmployee = new Employee();
        adminEmployee.setId(77L);
        adminEmployee.setUser(admin);
        adminEmployee.setRemunerationType(RemunerationType.COMISSIONADO);
        adminEmployee.setBookable(true);
        when(employeeRepository.findByUserId(50L)).thenReturn(Optional.of(adminEmployee));
        when(employeeRepository.save(any(Employee.class))).thenAnswer(inv -> inv.getArgument(0));

        var result = employeeService.setMyActing(false);

        assertThat(result.hasProfile()).isTrue();
        assertThat(result.acting()).isFalse();
        assertThat(adminEmployee.isBookable()).isFalse();
    }

    @Test
    void setMyActing_whenNotAdmin_shouldThrowBadRequest() {
        authenticateAs(staffUser); // FUNCIONARIA

        assertThatThrownBy(() -> employeeService.setMyActing(true))
                .isInstanceOf(BadRequestException.class);
        verify(employeeRepository, never()).save(any());
    }
}
