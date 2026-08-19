package com.cristiane.salon.models.service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record ServiceProductUsageRequest(
        @NotNull(message = "O produto é obrigatório")
        Long productId,

        @NotNull(message = "A quantidade consumida é obrigatória")
        @DecimalMin(value = "0.01", message = "A quantidade consumida deve ser maior que zero")
        BigDecimal quantityUsed
) {
}
