package com.cristiane.salon.models.staff.factory;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.staff.dto.StaffProfileRequest;
import com.cristiane.salon.models.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FuncionariaStrategyTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private FuncionariaStrategy strategy;

    private StaffProfileRequest requestWith(RemunerationType type, BigDecimal remunerationValue) {
        return new StaffProfileRequest(
                "Maria", "maria@example.com", "Senha@123", "FUNCIONARIA",
                "Maria Silva", null, "111.444.777-35", LocalDate.of(1990, 1, 1), null,
                "81999998888", null, null,
                "50000-000", "Rua A", "10", null, "Boa Vista", "Recife", null,
                null, null,
                LocalDate.now(), null,
                type, remunerationValue
        );
    }

    @Test
    void getRoleName_shouldBeFuncionaria() {
        assertThat(strategy.getRoleName()).isEqualTo("FUNCIONARIA");
    }

    @Test
    void validate_whenRemunerationTypeMissing_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(requestWith(null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tipo de remuneração é obrigatório");
    }

    @Test
    void validate_whenRemunerationValueMissing_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.SALARIO_FIXO, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valor do salário é obrigatório");
    }

    @Test
    void validate_whenHybridWithoutRemunerationValue_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.FIXO_E_COMISSIONADO, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valor do salário é obrigatório");
    }

    @Test
    void validate_whenComissionado_shouldNotRequireRemunerationValue() {
        strategy.validate(requestWith(RemunerationType.COMISSIONADO, null));
    }

    @Test
    void validate_whenValidFixedSalary_shouldNotThrow() {
        strategy.validate(requestWith(RemunerationType.SALARIO_FIXO, new BigDecimal("2000")));
    }

    @Test
    void onStaffCreated_shouldPersistEmployeeWithRemunerationFromRequest() {
        User user = new User();
        user.setId(5L);
        StaffProfileRequest request = requestWith(RemunerationType.FIXO_E_COMISSIONADO, new BigDecimal("2000"));

        strategy.onStaffCreated(user, request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getRemunerationType()).isEqualTo(RemunerationType.FIXO_E_COMISSIONADO);
        assertThat(saved.getRemunerationValue()).isEqualByComparingTo("2000");
    }

    @Test
    void onStaffCreated_whenComissionado_shouldNotPersistRemunerationValue() {
        User user = new User();
        user.setId(5L);
        StaffProfileRequest request = requestWith(RemunerationType.COMISSIONADO, null);

        strategy.onStaffCreated(user, request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        assertThat(captor.getValue().getRemunerationValue()).isNull();
    }
}
