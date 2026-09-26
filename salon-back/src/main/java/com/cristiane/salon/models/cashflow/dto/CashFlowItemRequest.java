package com.cristiane.salon.models.cashflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record CashFlowItemRequest(
        @NotNull(message = "O ID do produto é obrigatório")
        Long productId,

        @NotNull(message = "A quantidade é obrigatória")
        @Min(value = 1, message = "A quantidade mínima é 1")
        Integer quantity,

        /** Sobrescreve o preço unitário do produto só nesta venda (nulo = usa o valor do
         * catálogo) — ex.: fez um preço especial de R$ 85 pra um produto de R$ 100 de catálogo. */
        BigDecimal customPrice
) {}
