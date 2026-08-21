package com.cristiane.salon.models.service.dto;

import com.cristiane.salon.models.service.entity.SalonService;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

public record SalonServiceResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Boolean active,
        List<ServiceProductUsageResponse> productUsages,
        /** Soma dos custos conhecidos da receita — parcial se algum produto não tem custo/capacidade cadastrados. */
        BigDecimal estimatedProductCost,
        BigDecimal commissionPercent
) {
    public static SalonServiceResponse fromEntity(SalonService service) {
        return fromEntity(service, Collections.emptyList());
    }

    public static SalonServiceResponse fromEntity(SalonService service, List<ServiceProductUsageResponse> productUsages) {
        BigDecimal totalCost = productUsages.stream()
                .map(ServiceProductUsageResponse::estimatedCost)
                .filter(cost -> cost != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new SalonServiceResponse(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getPrice(),
                service.getActive(),
                productUsages,
                totalCost,
                service.getCommissionPercent()
        );
    }
}
