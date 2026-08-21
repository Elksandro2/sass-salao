package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SplitPaymentResolverTest {

    @Mock
    private SplitPaymentCalculator calculator;

    @Mock
    private EmployeeMercadoPagoConnectionService connectionService;

    private SplitPaymentResolver resolver;

    private Employee employee;
    private Appointment appointment;

    @BeforeEach
    void setUp() {
        resolver = new SplitPaymentResolver(calculator, connectionService);
        employee = new Employee();
        employee.setId(7L);
        employee.setRemunerationType(RemunerationType.COMISSIONADO);
        appointment = new Appointment();
    }

    @Test
    void resolve_whenEmployeeIsNull_shouldReturnEmpty() {
        assertThat(resolver.resolve(appointment, null)).isEmpty();
        verifyNoInteractions(calculator, connectionService);
    }

    @Test
    void resolve_whenCalculatorReturnsEmpty_shouldReturnEmptyWithoutCheckingConnection() {
        when(calculator.calculate(any(), eq(employee))).thenReturn(Optional.empty());

        assertThat(resolver.resolve(appointment, employee)).isEmpty();
        verifyNoInteractions(connectionService);
    }

    @Test
    void resolve_whenNotConnected_shouldReturnEmpty() {
        when(calculator.calculate(any(), eq(employee)))
                .thenReturn(Optional.of(new SplitPaymentCalculator.SplitResult(new BigDecimal("34.50"), new BigDecimal("15.00"))));
        when(connectionService.resolveValidAccessToken(7L)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(appointment, employee)).isEmpty();
    }

    @Test
    void resolve_whenBothConditionsMet_shouldReturnSplitPaymentInfo() {
        when(calculator.calculate(any(), eq(employee)))
                .thenReturn(Optional.of(new SplitPaymentCalculator.SplitResult(new BigDecimal("34.50"), new BigDecimal("15.00"))));
        when(connectionService.resolveValidAccessToken(7L)).thenReturn(Optional.of("APP_USR-token-abc"));

        Optional<SplitPaymentResolver.SplitPaymentInfo> result = resolver.resolve(appointment, employee);

        assertThat(result).isPresent();
        assertThat(result.get().applicationFee()).isEqualByComparingTo("34.50");
        assertThat(result.get().employeeShare()).isEqualByComparingTo("15.00");
        assertThat(result.get().sellerAccessToken()).isEqualTo("APP_USR-token-abc");
    }
}
