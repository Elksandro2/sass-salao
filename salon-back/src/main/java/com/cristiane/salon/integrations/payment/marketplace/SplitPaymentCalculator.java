package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;
import com.cristiane.salon.models.appointment.entity.AppointmentServiceItem;
import com.cristiane.salon.models.businesssettings.service.SalonBusinessSettingsService;
import com.cristiane.salon.models.employee.entity.Employee;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Calcula a divisão de um pagamento entre salão e funcionária, protegendo o valor dela da
 * taxa do Mercado Pago (a variação sempre cai na comissão do salão, nunca no que ela recebe).
 *
 * <p>A comissão dela nesse atendimento é a soma de duas fontes independentes: comissão de
 * serviço (% de cada {@link com.cristiane.salon.models.service.entity.SalonService} realizado,
 * só para COMISSIONADO/FIXO_E_COMISSIONADO) + comissão de produto (% única do salão sobre
 * produtos vendidos, vale pra qualquer tipo de remuneração — inclusive Salário Fixo).
 */
@Component
@RequiredArgsConstructor
public class SplitPaymentCalculator {

    private final MercadoPagoSplitProperties splitProperties;
    private final SalonBusinessSettingsService businessSettingsService;

    public record SplitResult(BigDecimal applicationFee, BigDecimal employeeShare) {
    }

    public Optional<SplitResult> calculate(Appointment appointment, Employee employee) {
        BigDecimal grossAmount = appointment.getGrandTotal();
        BigDecimal employeeShare = computeEmployeeShare(appointment, employee);

        if (employeeShare == null || employeeShare.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal mpFeeEstimate = grossAmount
                .multiply(splitProperties.getPixFeeRate())
                .setScale(2, RoundingMode.HALF_UP);

        BigDecimal applicationFee = grossAmount.subtract(mpFeeEstimate).subtract(employeeShare);

        // Comissão alta o suficiente pra não sobrar nada (ou sobrar negativo) pro salão depois
        // da taxa — não é um cenário normal, mas se acontecer não faz split: cai no fluxo
        // manual em vez de mandar um application_fee negativo pro Mercado Pago (ele recusaria).
        if (applicationFee.compareTo(BigDecimal.ZERO) < 0) {
            return Optional.empty();
        }

        return Optional.of(new SplitResult(applicationFee, employeeShare));
    }

    private BigDecimal computeEmployeeShare(Appointment appointment, Employee employee) {
        if (employee == null || employee.getRemunerationType() == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal serviceCommission = BigDecimal.ZERO;
        if (employee.getRemunerationType().paysServiceCommission()) {
            for (AppointmentServiceItem item : appointment.getServices()) {
                BigDecimal pct = item.getEffectiveCommissionPercent();
                if (pct == null) continue;
                BigDecimal price = item.getEffectivePrice() != null ? item.getEffectivePrice() : BigDecimal.ZERO;
                serviceCommission = serviceCommission.add(
                        price.multiply(pct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            }
        }

        // Comissão de produto é exceção universal — vale pra qualquer tipo de remuneração,
        // inclusive Salário Fixo, como incentivo à venda. Usa o % congelado no agendamento
        // (ver V72); agendamento sem snapshot cai no % atual do salão.
        BigDecimal productPct = appointment.getSnapshotProductCommissionPercent() != null
                ? appointment.getSnapshotProductCommissionPercent()
                : businessSettingsService.getProductCommissionPercent();
        BigDecimal productCommission = BigDecimal.ZERO;
        if (productPct != null && productPct.compareTo(BigDecimal.ZERO) > 0) {
            for (AppointmentProductItem item : appointment.getProducts()) {
                BigDecimal price = item.getEffectiveTotalPrice() != null ? item.getEffectiveTotalPrice() : BigDecimal.ZERO;
                productCommission = productCommission.add(
                        price.multiply(productPct).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP));
            }
        }

        return serviceCommission.add(productCommission);
    }
}
