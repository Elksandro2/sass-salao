package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.entity.AppointmentProductItem;

import java.math.BigDecimal;

public record AppointmentProductResponse(
        Long productId,
        String productName,
        BigDecimal catalogPrice,
        Integer quantity,
        BigDecimal customPrice,
        BigDecimal effectiveUnitPrice,
        BigDecimal effectiveTotalPrice
) {
    public static AppointmentProductResponse fromEntity(AppointmentProductItem item) {
        return new AppointmentProductResponse(
                item.getProduct().getId(),
                item.getProduct().getName(),
                item.getProduct().getPrice(),
                item.getQuantity(),
                item.getCustomPrice(),
                item.getEffectiveUnitPrice(),
                item.getEffectiveTotalPrice()
        );
    }
}
