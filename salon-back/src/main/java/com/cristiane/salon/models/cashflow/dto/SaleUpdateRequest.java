package com.cristiane.salon.models.cashflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Edição de uma venda avulsa de produto.
 *
 * @param items            itens da venda; vazio/null = venda antiga sem itens, edita pelo {@code amount}
 * @param amount           total — só usado quando não há itens
 * @param employeeId       quem vendeu (null = sem vendedora, sem comissão)
 * @param commissionAmount comissão digitada à mão; null = calcula pelo % da venda
 */
public record SaleUpdateRequest(
        @NotNull(message = "A data é obrigatória") LocalDate date,
        Long employeeId,
        @Valid List<Item> items,
        @DecimalMin(value = "0", message = "O valor não pode ser negativo") BigDecimal amount,
        @Size(max = 500, message = "A descrição deve ter no máximo 500 caracteres") String description,
        @DecimalMin(value = "0", message = "A comissão não pode ser negativa") BigDecimal commissionAmount
) {
    public record Item(
            @NotNull(message = "O produto é obrigatório") Long productId,
            @NotNull(message = "A quantidade é obrigatória") @Min(value = 1, message = "A quantidade mínima é 1") Integer quantity,
            /** null = preço de venda atual do produto. */
            @DecimalMin(value = "0", message = "O preço não pode ser negativo") BigDecimal unitPrice
    ) {}
}
