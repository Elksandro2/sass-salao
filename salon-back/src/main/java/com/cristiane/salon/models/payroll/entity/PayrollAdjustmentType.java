package com.cristiane.salon.models.payroll.entity;

/**
 * Tipos de lançamento avulso na folha. O valor do lançamento é sempre positivo — o tipo é que
 * diz se soma ou abate.
 */
public enum PayrollAdjustmentType {
    BONIFICACAO,
    DECIMO_TERCEIRO,
    FERIAS,
    HORA_EXTRA,
    OUTRO_ACRESCIMO,
    /** Adiantamento já entregue à colaboradora: abate do que falta pagar, mas continua sendo
     *  custo do salão (o dinheiro saiu, só saiu antes). */
    VALE,
    /** Desconto de fato (falta, dano, etc.): abate do que falta pagar E do custo do salão. */
    DESCONTO;

    public boolean isAddition() {
        return this != VALE && this != DESCONTO;
    }
}
