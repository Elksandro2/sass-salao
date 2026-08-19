package com.cristiane.salon.models.fixedexpense.dto;

import com.cristiane.salon.models.fixedexpense.entity.FixedExpense;

import java.math.BigDecimal;
import java.time.LocalDate;

public record FixedExpenseResponse(
        Long id,
        String description,
        BigDecimal amount,
        LocalDate date
) {
    public static FixedExpenseResponse fromEntity(FixedExpense entity) {
        return new FixedExpenseResponse(entity.getId(), entity.getDescription(), entity.getAmount(), entity.getDate());
    }
}
