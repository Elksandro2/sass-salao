package com.cristiane.salon.models.product.entity;

import java.math.BigDecimal;

/** Unidade em que a capacidade/embalagem do produto é medida — base pro cálculo de custo por uso. */
public enum ProductUnit {
    ML,
    L,
    G,
    KG,
    UNIDADE;

    /**
     * Fator pra converter 1 {@code this} em quantas unidades {@code to} — ex.:
     * {@code ML.factorTo(L)} = 0.001 (1ml = 0.001L). {@code null} quando as unidades não são da
     * mesma grandeza (ex.: ML pra G não faz sentido — volume e massa são coisas diferentes).
     */
    public BigDecimal factorTo(ProductUnit to) {
        // to == null: produto sem unidade cadastrada (mas com custo/capacidade) — trata como
        // "mesma unidade", igual o comportamento de sempre antes desta conversão existir.
        if (to == null || this == to) {
            return BigDecimal.ONE;
        }
        if (this == ML && to == L) return new BigDecimal("0.001");
        if (this == L && to == ML) return new BigDecimal("1000");
        if (this == G && to == KG) return new BigDecimal("0.001");
        if (this == KG && to == G) return new BigDecimal("1000");
        return null;
    }
}
