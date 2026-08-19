package com.cristiane.salon.models.fixedexpense.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedExpenseRequest(
        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
        String description,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0", inclusive = true, message = "O valor não pode ser negativo")
        BigDecimal amount,

        @NotNull(message = "A data é obrigatória")
        LocalDate date
) {
}
