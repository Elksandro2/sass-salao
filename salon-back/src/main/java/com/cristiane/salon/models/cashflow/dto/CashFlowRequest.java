package com.cristiane.salon.models.cashflow.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record CashFlowRequest(
        @NotBlank(message = "O tipo é obrigatório (INCOME ou EXPENSE)")
        String type,

        @NotNull(message = "O valor é obrigatório")
        @Min(value = 0, message = "O valor não pode ser negativo")
        BigDecimal amount,

        @NotBlank(message = "A descrição é obrigatória")
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres")
        String description,

        @NotNull(message = "A data é obrigatória")
        LocalDate date,
        
        Long appointmentId,

        List<CashFlowItemRequest> items,

        /** Quem vendeu, numa venda avulsa de produto — opcional, usado só pra calcular comissão. */
        Long employeeId
) {}
