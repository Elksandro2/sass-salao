package com.cristiane.salon.models.service.dto;

import com.cristiane.salon.models.product.entity.ProductUnit;
import com.cristiane.salon.models.service.entity.SalonServiceProductUsage;

import java.math.BigDecimal;

public record ServiceProductUsageResponse(
        Long productId,
        String productName,
        BigDecimal quantityUsed,
        ProductUnit unit,
        BigDecimal estimatedCost
) {
    public static ServiceProductUsageResponse fromEntity(SalonServiceProductUsage usage) {
        return new ServiceProductUsageResponse(
                usage.getProduct().getId(),
                usage.getProduct().getName(),
                usage.getQuantityUsed(),
                usage.getProduct().getUnit(),
                usage.getEstimatedCost()
        );
    }
}
