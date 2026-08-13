package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AppointmentProductRequest(
        @NotNull(message = "O produto é obrigatório")
        Long productId,

        @NotNull(message = "A quantidade é obrigatória")
        @Min(value = 1, message = "A quantidade deve ser maior que zero")
        Integer quantity,

        /** Sobrescreve o preço unitário do produto só para este item (nulo = usa o valor do catálogo). */
        BigDecimal customPrice
) {
}
