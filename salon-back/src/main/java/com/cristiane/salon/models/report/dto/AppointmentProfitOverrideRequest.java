package com.cristiane.salon.models.report.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Valores manuais do lucro de um atendimento. Cada campo null = volta ao cálculo automático.
 * Mandar tudo null limpa a edição manual inteira.
 */
public record AppointmentProfitOverrideRequest(
        @DecimalMin(value = "0", message = "O custo de produtos da receita não pode ser negativo")
        BigDecimal serviceRecipeCost,
        @DecimalMin(value = "0", message = "O custo dos produtos vendidos não pode ser negativo")
        BigDecimal productsSoldCost,
        @DecimalMin(value = "0", message = "A comissão sobre serviços não pode ser negativa")
        BigDecimal serviceCommissionCost,
        @DecimalMin(value = "0", message = "A comissão sobre produtos não pode ser negativa")
        BigDecimal productCommissionCost,
        @Size(max = 500, message = "A justificativa deve ter no máximo 500 caracteres")
        String note
) {}
