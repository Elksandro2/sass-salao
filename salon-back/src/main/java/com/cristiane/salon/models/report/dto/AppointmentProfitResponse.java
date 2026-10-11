package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;

/**
 * Lucro/prejuízo de um atendimento específico: quanto foi cobrado menos o custo dos produtos
 * consumidos (receita do serviço + produtos vendidos) e a comissão da profissional. Não inclui
 * rateio de gastos fixos — isso é feito só na análise agregada por tipo de serviço.
 *
 * <p>Cada custo pode ter sido digitado à mão só para este atendimento (flags {@code *Manual});
 * os campos {@code auto*} trazem sempre o valor que o sistema calcularia, pra tela mostrar a
 * diferença e permitir voltar ao automático.
 */
public record AppointmentProfitResponse(
        Long appointmentId,
        BigDecimal grossRevenue,
        BigDecimal serviceRecipeCost,
        BigDecimal productsSoldCost,
        BigDecimal serviceCommissionCost,
        BigDecimal productCommissionCost,
        BigDecimal netProfit,
        boolean positive,
        BigDecimal autoServiceRecipeCost,
        BigDecimal autoProductsSoldCost,
        BigDecimal autoServiceCommissionCost,
        BigDecimal autoProductCommissionCost,
        boolean serviceRecipeCostManual,
        boolean productsSoldCostManual,
        boolean serviceCommissionCostManual,
        boolean productCommissionCostManual,
        String overrideNote
) {
    /** Sem nenhum valor manual — os quatro custos são os automáticos. */
    public static AppointmentProfitResponse of(
            Long appointmentId, BigDecimal grossRevenue, BigDecimal serviceRecipeCost,
            BigDecimal productsSoldCost, BigDecimal serviceCommissionCost, BigDecimal productCommissionCost) {
        return of(appointmentId, grossRevenue,
                serviceRecipeCost, null, productsSoldCost, null,
                serviceCommissionCost, null, productCommissionCost, null, null);
    }

    /** {@code override*} null = usa o automático correspondente. */
    public static AppointmentProfitResponse of(
            Long appointmentId, BigDecimal grossRevenue,
            BigDecimal autoRecipe, BigDecimal overrideRecipe,
            BigDecimal autoProductsSold, BigDecimal overrideProductsSold,
            BigDecimal autoServiceCommission, BigDecimal overrideServiceCommission,
            BigDecimal autoProductCommission, BigDecimal overrideProductCommission,
            String overrideNote) {
        BigDecimal recipe = overrideRecipe != null ? overrideRecipe : autoRecipe;
        BigDecimal productsSold = overrideProductsSold != null ? overrideProductsSold : autoProductsSold;
        BigDecimal serviceCommission = overrideServiceCommission != null ? overrideServiceCommission : autoServiceCommission;
        BigDecimal productCommission = overrideProductCommission != null ? overrideProductCommission : autoProductCommission;
        BigDecimal netProfit = grossRevenue
                .subtract(recipe)
                .subtract(productsSold)
                .subtract(serviceCommission)
                .subtract(productCommission);
        return new AppointmentProfitResponse(
                appointmentId, grossRevenue, recipe, productsSold, serviceCommission, productCommission,
                netProfit, netProfit.signum() >= 0,
                autoRecipe, autoProductsSold, autoServiceCommission, autoProductCommission,
                overrideRecipe != null, overrideProductsSold != null,
                overrideServiceCommission != null, overrideProductCommission != null,
                overrideNote);
    }
}
