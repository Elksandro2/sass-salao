package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.entity.AppointmentExpenseItem;

import java.math.BigDecimal;

public record AppointmentExpenseResponse(
        Long id,
        String description,
        String valueType,
        BigDecimal value,
        BigDecimal effectiveAmount
) {
    public static AppointmentExpenseResponse fromEntity(AppointmentExpenseItem item, BigDecimal baseAmount) {
        return new AppointmentExpenseResponse(
                item.getId(),
                item.getDescription(),
                item.getValueType().name(),
                item.getValue(),
                item.getEffectiveAmount(baseAmount)
        );
    }
}
