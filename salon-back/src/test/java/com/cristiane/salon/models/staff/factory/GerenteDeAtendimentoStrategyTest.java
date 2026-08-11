package com.cristiane.salon.models.staff.factory;

import com.cristiane.salon.exception.BadRequestException;
import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.employee.repository.EmployeeRepository;
import com.cristiane.salon.models.staff.dto.StaffProfileRequest;
import com.cristiane.salon.models.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class GerenteDeAtendimentoStrategyTest {

    @Mock
    private EmployeeRepository employeeRepository;

    private GerenteDeAtendimentoStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GerenteDeAtendimentoStrategy(employeeRepository);
    }

    private StaffProfileRequest requestWith(RemunerationType type, CommissionScope scope, BigDecimal value) {
        return new StaffProfileRequest(
                "Ana", "ana@example.com", "Senha@123", "GERENTE_DE_ATENDIMENTO",
                "Ana Souza", null, "111.444.777-35", LocalDate.of(1988, 1, 1), null,
                "81999998888", null, null,
                "50000-000", "Rua A", "10", null, "Boa Vista", "Recife", null,
                null, null,
                LocalDate.now(), null,
                type, scope,
                value, null, null
        );
    }

    @Test
    void getRoleName_shouldBeGerenteDeAtendimento() {
        assertThat(strategy.getRoleName()).isEqualTo("GERENTE_DE_ATENDIMENTO");
    }

    @Test
    void validate_whenNoRemunerationFieldsSet_shouldNotThrow() {
        strategy.validate(requestWith(null, null, null));
    }

    @Test
    void validate_whenSalarioFixoWithValue_shouldNotThrow() {
        strategy.validate(requestWith(RemunerationType.SALARIO_FIXO, null, new BigDecimal("3000")));
    }

    @Test
    void validate_whenSalarioFixoWithoutValue_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(requestWith(RemunerationType.SALARIO_FIXO, null, null)))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("valor do salário fixo");
    }

    @Test
    void validate_whenComissionado_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.COMISSIONADO, CommissionScope.INDIVIDUAL, new BigDecimal("10"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Salário Fixo");
    }

    @Test
    void validate_whenCommissionScopeSet_shouldThrow() {
        assertThatThrownBy(() -> strategy.validate(
                requestWith(RemunerationType.SALARIO_FIXO, CommissionScope.GLOBAL, new BigDecimal("3000"))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("comissão não se aplicam");
    }

    @Test
    void onStaffCreated_whenSalarioFixo_shouldCreateNonBookableEmployee() {
        User user = new User();
        user.setId(1L);

        strategy.onStaffCreated(user, requestWith(RemunerationType.SALARIO_FIXO, null, new BigDecimal("3000")));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());
        Employee saved = captor.getValue();
        assertThat(saved.getUser()).isEqualTo(user);
        assertThat(saved.isBookable()).isFalse();
        assertThat(saved.getRemunerationType()).isEqualTo(RemunerationType.SALARIO_FIXO);
        assertThat(saved.getRemunerationValue()).isEqualByComparingTo("3000");
    }

    @Test
    void onStaffCreated_whenNoRemuneration_shouldNotCreateEmployee() {
        User user = new User();
        user.setId(1L);

        strategy.onStaffCreated(user, requestWith(null, null, null));

        verify(employeeRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }
}
