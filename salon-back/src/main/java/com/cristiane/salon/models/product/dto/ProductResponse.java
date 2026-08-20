package com.cristiane.salon.models.product.dto;

import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.entity.ProductUnit;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        BigDecimal price,
        Boolean active,
        String brand,
        BigDecimal costPrice,
        BigDecimal capacity,
        ProductUnit unit,
        BigDecimal unitCost,
        Boolean availableForSale,
        Boolean usedInServiceRecipe
) {
    public static ProductResponse fromEntity(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getActive(),
                product.getBrand(),
                product.getCostPrice(),
                product.getCapacity(),
                product.getUnit(),
                product.getUnitCost(),
                product.getAvailableForSale(),
                product.getUsedInServiceRecipe()
        );
    }
}
