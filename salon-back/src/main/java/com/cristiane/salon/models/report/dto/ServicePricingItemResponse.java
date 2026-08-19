package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Saúde financeira de UM tipo de serviço do catálogo no período analisado: quanto ele rendeu,
 * quanto custou (receita de produtos + comissão + fatia dos gastos fixos) e se está dando lucro
 * ou prejuízo — a pergunta que fica é "tô cobrando certo por esse serviço ou preciso reajustar?".
 */
public record ServicePricingItemResponse(
        Long serviceId,
        String serviceName,
        BigDecimal catalogPrice,
        long timesPerformed,
        BigDecimal totalRevenue,
        /** Custo total dos produtos consumidos (receita do serviço) nas execuções do período. */
        BigDecimal recipeCostTotal,
        /** Comissão estimada das profissionais sobre este serviço no período. */
        BigDecimal commissionCostTotal,
        /** Fatia dos gastos fixos do período rateada proporcionalmente à receita deste serviço. */
        BigDecimal fixedExpenseShare,
        BigDecimal netProfit,
        /** Margem líquida em % sobre a receita deste serviço. */
        BigDecimal marginPercent,
        boolean healthy
) {
    public static ServicePricingItemResponse of(
            Long serviceId, String serviceName, BigDecimal catalogPrice, long timesPerformed,
            BigDecimal totalRevenue, BigDecimal recipeCostTotal, BigDecimal commissionCostTotal,
            BigDecimal fixedExpenseShare) {
        BigDecimal netProfit = totalRevenue
                .subtract(recipeCostTotal)
                .subtract(commissionCostTotal)
                .subtract(fixedExpenseShare);
        BigDecimal marginPercent = totalRevenue.signum() > 0
                ? netProfit.multiply(new BigDecimal("100")).divide(totalRevenue, 1, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        return new ServicePricingItemResponse(
                serviceId, serviceName, catalogPrice, timesPerformed, totalRevenue,
                recipeCostTotal, commissionCostTotal, fixedExpenseShare, netProfit, marginPercent,
                netProfit.signum() >= 0);
    }
}
