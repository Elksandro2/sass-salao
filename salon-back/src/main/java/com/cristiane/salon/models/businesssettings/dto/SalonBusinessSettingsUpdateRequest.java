package com.cristiane.salon.models.businesssettings.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record SalonBusinessSettingsUpdateRequest(
        @DecimalMin(value = "0.0", message = "A comissão de produto não pode ser negativa")
        @DecimalMax(value = "100.0", message = "A comissão de produto não pode exceder 100%")
        BigDecimal productCommissionPercent
) {}
