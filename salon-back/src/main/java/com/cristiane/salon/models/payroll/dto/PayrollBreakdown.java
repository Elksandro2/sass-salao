package com.cristiane.salon.models.payroll.dto;

import java.math.BigDecimal;

/**
 * Como a remuneração de uma colaboradora num período foi composta — cada parcela separada,
 * pra tela mostrar de onde veio cada real.
 *
 * <p>Dois totais diferentes, de propósito:
 * <ul>
 *   <li>{@link #salonCost()} — quanto a folha dela custa ao salão no período (entra no lucro do
 *   relatório financeiro). Vale NÃO abate: é dinheiro que já saiu, só saiu antes.</li>
 *   <li>{@link #netPayable()} — quanto ainda falta entregar a ela: o custo menos os vales.</li>
 * </ul>
 *
 * @param fixedSalary                  salário fixo mensal proporcional aos dias do período
 * @param dailyTotal                   soma das diárias marcadas no período (cada dia com o próprio valor)
 * @param appointmentProductCommission comissão sobre produtos vendidos dentro de atendimentos
 * @param saleProductCommission        comissão sobre vendas avulsas de produto (Fluxo de Caixa)
 * @param additions                    bonificação, 13º, férias, hora extra, outros acréscimos
 * @param discounts                    descontos de fato (falta, dano...)
 * @param advances                     vales/adiantamentos já entregues
 */
public record PayrollBreakdown(
        BigDecimal fixedSalary,
        int workDaysCount,
        BigDecimal dailyTotal,
        BigDecimal serviceCommission,
        BigDecimal appointmentProductCommission,
        BigDecimal saleProductCommission,
        BigDecimal additions,
        BigDecimal discounts,
        BigDecimal advances
) {
    public BigDecimal totalCommission() {
        return serviceCommission.add(appointmentProductCommission).add(saleProductCommission);
    }

    /** Salário + diárias + acréscimos − descontos: a parte "não comissão" do custo. */
    public BigDecimal baseAndExtras() {
        return fixedSalary.add(dailyTotal).add(additions).subtract(discounts);
    }

    public BigDecimal salonCost() {
        return baseAndExtras().add(totalCommission());
    }

    public BigDecimal netPayable() {
        return salonCost().subtract(advances);
    }
}
