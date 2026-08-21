package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.service.entity.SalonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SplitPaymentCalculatorTest {

    @Mock
    private SalonBusinessSettingsService businessSettingsService;

    private MercadoPagoSplitProperties properties;
    private SplitPaymentCalculator calculator;

    @BeforeEach
    void setUp() {
        properties = new MercadoPagoSplitProperties();
        properties.setPixFeeRate(new BigDecimal("0.0099"));
        calculator = new SplitPaymentCalculator(properties, businessSettingsService);
        lenient().when(businessSettingsService.getProductCommissionPercent()).thenReturn(null);
    }

    private Employee employeeWith(RemunerationType type) {
        Employee employee = new Employee();
        employee.setRemunerationType(type);
        return employee;
    }

    private Appointment appointmentWithService(BigDecimal price, BigDecimal commissionPercent) {
        SalonService salonService = new SalonService();
        salonService.setPrice(price);
        salonService.setCommissionPercent(commissionPercent);

        AppointmentServiceItem item = new AppointmentServiceItem();
        item.setSalonService(salonService);

        Appointment appointment = new Appointment();
        appointment.setServices(List.of(item));
        appointment.setProducts(List.of());
        return appointment;
    }

    @Test
    void calculate_whenComissionado_shouldProtectEmployeeShareAndAbsorbFeeOnSalao() {
        // 30% de comissão sobre R$ 50, taxa de 0,99%
        Appointment appointment = appointmentWithService(new BigDecimal("50.00"), new BigDecimal("30"));
        Employee employee = employeeWith(RemunerationType.COMISSIONADO);

        Optional<SplitPaymentCalculator.SplitResult> result = calculator.calculate(appointment, employee);

        assertThat(result).isPresent();
        // Funcionária: exatamente 30% do BRUTO — nunca varia com a taxa.
        assertThat(result.get().employeeShare()).isEqualByComparingTo("15.00");
        // Taxa estimada: 50 * 0.0099 = 0.495 -> 0.50 (arredondado)
        // application_fee = 50.00 - 0.50 - 15.00 = 34.50 (o salão absorve a taxa)
        assertThat(result.get().applicationFee()).isEqualByComparingTo("34.50");
    }

    @Test
    void calculate_whenFixoEComissionado_shouldUseServiceCommissionPercent() {
        Appointment appointment = appointmentWithService(new BigDecimal("100.00"), new BigDecimal("10"));
        Employee employee = employeeWith(RemunerationType.FIXO_E_COMISSIONADO);

        Optional<SplitPaymentCalculator.SplitResult> result = calculator.calculate(appointment, employee);

        assertThat(result).isPresent();
        assertThat(result.get().employeeShare()).isEqualByComparingTo("10.00");
    }

    @Test
    void calculate_whenSalarioFixoAndNoProductCommission_shouldReturnEmpty() {
        // Salário Fixo nunca recebe comissão de serviço — mesmo com % configurado no serviço.
        Appointment appointment = appointmentWithService(new BigDecimal("50.00"), new BigDecimal("30"));
        Employee employee = employeeWith(RemunerationType.SALARIO_FIXO);

        assertThat(calculator.calculate(appointment, employee)).isEmpty();
    }

    @Test
    void calculate_whenSalarioFixoAndProductSold_shouldReceiveProductCommission() {
        // Exceção universal: produto paga comissão pra qualquer tipo, inclusive Salário Fixo.
        lenient().when(businessSettingsService.getProductCommissionPercent()).thenReturn(new BigDecimal("5"));

        Product product = new Product();
        product.setPrice(new BigDecimal("100.00"));
        AppointmentProductItem productItem = new AppointmentProductItem();
        productItem.setProduct(product);
        productItem.setQuantity(1);

        Appointment appointment = new Appointment();
        appointment.setServices(List.of());
        appointment.setProducts(List.of(productItem));

        Employee employee = employeeWith(RemunerationType.SALARIO_FIXO);

        Optional<SplitPaymentCalculator.SplitResult> result = calculator.calculate(appointment, employee);

        assertThat(result).isPresent();
        assertThat(result.get().employeeShare()).isEqualByComparingTo("5.00");
    }

    @Test
    void calculate_whenRemunerationTypeIsNull_shouldReturnEmpty() {
        Appointment appointment = appointmentWithService(new BigDecimal("50.00"), new BigDecimal("30"));
        Employee employee = employeeWith(null);

        assertThat(calculator.calculate(appointment, employee)).isEmpty();
    }

    @Test
    void calculate_whenCommissionPercentIsZeroOrNull_shouldReturnEmpty() {
        Appointment appointment = appointmentWithService(new BigDecimal("50.00"), null);
        Employee employee = employeeWith(RemunerationType.COMISSIONADO);

        assertThat(calculator.calculate(appointment, employee)).isEmpty();
    }

    @Test
    void calculate_whenCommissionIsTooHighToLeaveRoomForFee_shouldReturnEmpty() {
        // 100% de comissão: não sobra nada (nem pra cobrir a taxa) pro salão — não faz split.
        Appointment appointment = appointmentWithService(new BigDecimal("50.00"), new BigDecimal("100"));
        Employee employee = employeeWith(RemunerationType.COMISSIONADO);

        assertThat(calculator.calculate(appointment, employee)).isEmpty();
    }
}
