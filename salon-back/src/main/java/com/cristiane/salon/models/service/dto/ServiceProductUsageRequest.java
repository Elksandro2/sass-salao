package com.cristiane.salon.models.service.dto;

import com.cristiane.salon.models.product.entity.ProductUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ServiceProductUsageRequest(
        @NotNull(message = "O produto é obrigatório")
        Long productId,

        @NotNull(message = "A quantidade consumida é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade consumida deve ser maior que zero")
        BigDecimal quantityUsed,

        /**
         * Unidade em que {@code quantityUsed} foi lançado — independente da unidade cadastrada
         * no produto (ex.: produto embalado em Litro, mas a receita consome em ml). Omitido =
         * usa a unidade do próprio produto (comportamento de sempre).
         */
        ProductUnit unit
) {
}
