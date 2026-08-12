package com.cristiane.salon.models.staff.factory;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.models.employee.entity.CommissionScope;
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

    private StaffProfileRequest requestWith(RemunerationType type, CommissionScope scope,
                                             BigDecimal remunerationValue, BigDecimal commissionValue) {
        return new StaffProfileRequest(
                "Maria", "maria@example.com", "Senha@123", "FUNCIONARIA",
                "Maria Silva", null, "111.444.777-35", LocalDate.of(1990, 1, 1), null,
                "81999998888", null, null,
                "50000-000", "Rua A", "10", null, "Boa Vista", "Recife", null,
                null, null,
                LocalDate.now(), null,
                type, scope, remunerationValue, commissionValue
        );
    }

    @Test
    void getRoleName_shouldBeFuncionaria() {
        assertThat(strategy.getRoleName()).isEqualTo("FUNCIONARIA");
    }

    @Test
    void validate_whenRemunerationTypeMissing_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(requestWith(null, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("tipo de remuneração é obrigatório");
    }

    @Test
    void validate_whenCommissionedWithoutScope_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.COMISSIONADO, null, new BigDecimal("10"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("escopo da comissão é obrigatório");
    }

    @Test
    void validate_whenFixedSalaryWithScope_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.SALARIO_FIXO, CommissionScope.INDIVIDUAL, new BigDecimal("2000"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("só se aplica a funcionárias comissionadas");
    }

    @Test
    void validate_whenRemunerationValueMissing_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.SALARIO_FIXO, null, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valor de remuneração é obrigatório");
    }

    @Test
    void validate_whenCommissionPercentageOver100_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.COMISSIONADO, CommissionScope.GLOBAL, new BigDecimal("150"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não pode exceder 100%");
    }

    @Test
    void validate_whenHybridWithoutCommissionValue_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.FIXO_E_COMISSIONADO, CommissionScope.INDIVIDUAL,
                        new BigDecimal("2000"), null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("porcentagem de comissão é obrigatória");
    }

    @Test
    void validate_whenHybridCommissionValueOver100_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.FIXO_E_COMISSIONADO, CommissionScope.INDIVIDUAL,
                        new BigDecimal("2000"), new BigDecimal("101"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("não pode exceder 100%");
    }

    @Test
    void validate_whenValidFixedSalary_shouldNotThrow() {
        strategy.validate(requestWith(RemunerationType.SALARIO_FIXO, null, new BigDecimal("2000"), null));
    }

    @Test
    void onStaffCreated_shouldPersistEmployeeWithRemunerationFromRequest() {
        User user = new User();
        user.setId(5L);
        StaffProfileRequest request = requestWith(
                RemunerationType.FIXO_E_COMISSIONADO, CommissionScope.GLOBAL,
                new BigDecimal("2000"), new BigDecimal("15"));

        strategy.onStaffCreated(user, request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.getRemunerationType()).isEqualTo(RemunerationType.FIXO_E_COMISSIONADO);
        assertThat(saved.getCommissionScope()).isEqualTo(CommissionScope.GLOBAL);
        assertThat(saved.getRemunerationValue()).isEqualByComparingTo("2000");
        assertThat(saved.getCommissionValue()).isEqualByComparingTo("15");
    }

    @Test
    void onStaffCreated_whenFixedSalary_shouldNotPersistCommissionScopeOrValue() {
        User user = new User();
        user.setId(5L);
        StaffProfileRequest request = requestWith(RemunerationType.SALARIO_FIXO, null, new BigDecimal("2000"), null);

        strategy.onStaffCreated(user, request);

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        assertThat(captor.getValue().getCommissionScope()).isNull();
        assertThat(captor.getValue().getCommissionValue()).isNull();
    }
}
