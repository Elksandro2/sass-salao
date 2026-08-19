package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;

/**
 * Lucro/prejuízo de um atendimento específico: quanto foi cobrado menos o custo dos produtos
 * consumidos (receita do serviço + produtos vendidos) e a comissão da profissional. Não inclui
 * rateio de gastos fixos — isso é feito só na análise agregada por tipo de serviço.
 */
public record AppointmentProfitResponse(
        Long appointmentId,
        BigDecimal grossRevenue,
        /** Custo estimado dos produtos consumidos pra realizar os serviços (receita do serviço). */
        BigDecimal serviceRecipeCost,
        /** Custo dos produtos vendidos (retail) neste atendimento. */
        BigDecimal productsSoldCost,
        /** Comissão estimada da profissional sobre este atendimento específico. */
        BigDecimal commissionCost,
        BigDecimal netProfit,
        boolean positive
) {
    public static AppointmentProfitResponse of(
            Long appointmentId, BigDecimal grossRevenue, BigDecimal serviceRecipeCost,
            BigDecimal productsSoldCost, BigDecimal commissionCost) {
        BigDecimal netProfit = grossRevenue
                .subtract(serviceRecipeCost)
                .subtract(productsSoldCost)
                .subtract(commissionCost);
        return new AppointmentProfitResponse(
                appointmentId, grossRevenue, serviceRecipeCost, productsSoldCost, commissionCost,
                netProfit, netProfit.signum() >= 0);
    }
}
