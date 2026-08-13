package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SplitPaymentCalculatorTest {

    private MercadoPagoSplitProperties properties;
    private SplitPaymentCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new MercadoPagoSplitProperties();
        properties.setPixFeeRate(new BigDecimal("0.0099"));
        calculator = new SplitPaymentCalculator(properties);
    }

    private Employee employeeWith(RemunerationType type, CommissionScope scope, BigDecimal remunerationValue, BigDecimal commissionValue) {
        Employee employee = new Employee();
        employee.setRemunerationType(type);
        employee.setCommissionScope(scope);
        employee.setRemunerationValue(remunerationValue);
        employee.setCommissionValue(commissionValue);
        return employee;
    }

    @Test
    void calculate_whenComissionadoIndividual_shouldProtectEmployeeShareAndAbsorbFeeOnSalao() {
        // 30% de comissão sobre R$ 50, taxa de 0,99%
        Employee employee = employeeWith(RemunerationType.COMISSIONADO, CommissionScope.INDIVIDUAL,
                new BigDecimal("30"), null);

        Optional<SplitPaymentCalculator.SplitResult> result = calculator.calculate(new BigDecimal("50.00"), employee);

        assertThat(result).isPresent();
        // Funcionária: exatamente 30% do BRUTO — nunca varia com a taxa.
        assertThat(result.get().employeeShare()).isEqualByComparingTo("15.00");
        // Taxa estimada: 50 * 0.0099 = 0.495 -> 0.50 (arredondado)
        // application_fee = 50.00 - 0.50 - 15.00 = 34.50 (o salão absorve a taxa)
        assertThat(result.get().applicationFee()).isEqualByComparingTo("34.50");
    }

    @Test
    void calculate_whenFixoEComissionadoIndividual_shouldUseOnlyCommissionValuePercent() {
        // FIXO_E_COMISSIONADO: só a comissão adicional (commissionValue) entra no split por
        // atendimento — a parte de salário fixo (remunerationValue) é paga fora, mensalmente.
        Employee employee = employeeWith(RemunerationType.FIXO_E_COMISSIONADO, CommissionScope.INDIVIDUAL,
                new BigDecimal("2000"), new BigDecimal("10"));

        Optional<SplitPaymentCalculator.SplitResult> result = calculator.calculate(new BigDecimal("100.00"), employee);

        assertThat(result).isPresent();
        assertThat(result.get().employeeShare()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculate_whenSalarioFixo_shouldReturnEmpty() {
        Employee employee = employeeWith(RemunerationType.SALARIO_FIXO, null, new BigDecimal("2000"), null);

        assertThat(calculator.calculate(new BigDecimal("50.00"), employee)).isEmpty();
    }

    @Test
    void calculate_whenCommissionScopeIsGlobal_shouldReturnEmpty() {
        // Comissão GLOBAL é sobre o total do salão no período — não tem "valor deste
        // atendimento" que faça sentido isolar numa única transação.
        Employee employee = employeeWith(RemunerationType.COMISSIONADO, CommissionScope.GLOBAL,
                new BigDecimal("30"), null);

        assertThat(calculator.calculate(new BigDecimal("50.00"), employee)).isEmpty();
    }

    @Test
    void calculate_whenRemunerationTypeIsNull_shouldReturnEmpty() {
        Employee employee = employeeWith(null, null, null, null);

        assertThat(calculator.calculate(new BigDecimal("50.00"), employee)).isEmpty();
    }

    @Test
    void calculate_whenCommissionPercentIsZeroOrNull_shouldReturnEmpty() {
        Employee employee = employeeWith(RemunerationType.COMISSIONADO, CommissionScope.INDIVIDUAL,
                BigDecimal.ZERO, null);

        assertThat(calculator.calculate(new BigDecimal("50.00"), employee)).isEmpty();
    }

    @Test
    void calculate_whenCommissionIsTooHighToLeaveRoomForFee_shouldReturnEmpty() {
        // 100% de comissão: não sobra nada (nem pra cobrir a taxa) pro salão — não faz split.
        Employee employee = employeeWith(RemunerationType.COMISSIONADO, CommissionScope.INDIVIDUAL,
                new BigDecimal("100"), null);

        assertThat(calculator.calculate(new BigDecimal("50.00"), employee)).isEmpty();
    }
}
