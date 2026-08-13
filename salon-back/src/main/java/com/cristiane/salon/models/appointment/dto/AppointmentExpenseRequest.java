package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record AppointmentExpenseRequest(
        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 200, message = "A descrição deve ter no máximo 200 caracteres")
        String description,

        /** "FIXED" (valor em R$) ou "PERCENTAGE" (% sobre serviços+produtos do agendamento). */
        @NotBlank(message = "O tipo de valor é obrigatório")
        String valueType,

        @NotNull(message = "O valor é obrigatório")
        @DecimalMin(value = "0", inclusive = true, message = "O valor não pode ser negativo")
        BigDecimal value
) {
}
