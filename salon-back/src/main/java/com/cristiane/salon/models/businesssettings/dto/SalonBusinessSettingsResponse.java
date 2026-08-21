package com.cristiane.salon.models.businesssettings.dto;

import com.cristiane.salon.models.businesssettings.entity.SalonBusinessSettings;

import java.math.BigDecimal;
import java.time.Instant;

public record SalonBusinessSettingsResponse(
        BigDecimal productCommissionPercent,
        Instant updatedAt
) {
    public static SalonBusinessSettingsResponse fromEntity(SalonBusinessSettings settings) {
        return new SalonBusinessSettingsResponse(settings.getProductCommissionPercent(), settings.getUpdatedAt());
    }
}
