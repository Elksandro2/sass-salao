package com.cristiane.salon.integrations.payment.marketplace;

import com.cristiane.salon.models.employee.entity.CommissionScope;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.employee.entity.RemunerationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

/**
 * Calcula a divisão de um pagamento entre salão e funcionária, protegendo o valor dela da
 * taxa do Mercado Pago (a variação sempre cai na comissão do salão, nunca no que ela recebe).
 *
 * <p>Só faz sentido dividir POR ATENDIMENTO quando a comissão dela é individual sobre o valor
 * daquele serviço específico — ou seja, {@code COMISSIONADO}/{@code FIXO_E_COMISSIONADO} com
 * {@code commissionScope = INDIVIDUAL}. Salário fixo e comissão GLOBAL (calculada sobre o total
 * do salão no período) não têm um "valor desta transação" que faça sentido isolar — continuam
 * sendo pagos pelo fluxo manual existente (relatório + PIX avulso).
 */
@Component
@RequiredArgsConstructor
public class SplitPaymentCalculator {

    private final MercadoPagoSplitProperties splitProperties;

    public record SplitResult(BigDecimal applicationFee, BigDecimal employeeShare) {
    }

    public Optional<SplitResult> calculate(BigDecimal grossAmount, Employee employee) {
        BigDecimal commissionPercent = resolveIndividualCommissionPercent(employee);
        if (commissionPercent == null || commissionPercent.compareTo(BigDecimal.ZERO) <= 0) {
            return Optional.empty();
        }

        BigDecimal employeeShare = grossAmount
                .multiply(commissionPercent)
                .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);

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

    private BigDecimal resolveIndividualCommissionPercent(Employee employee) {
        RemunerationType type = employee.getRemunerationType();
        if (type == null || employee.getCommissionScope() != CommissionScope.INDIVIDUAL) {
            return null;
        }
        if (type == RemunerationType.COMISSIONADO) {
            return employee.getRemunerationValue();
        }
        if (type == RemunerationType.FIXO_E_COMISSIONADO) {
            return employee.getCommissionValue();
        }
        return null;
    }
}
