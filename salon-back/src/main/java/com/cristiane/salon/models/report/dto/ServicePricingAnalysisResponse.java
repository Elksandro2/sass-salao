package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * Análise de preço agregada por tipo de serviço do catálogo, com rateio dos gastos fixos —
 * diferente de {@link AppointmentProfitResponse}, que é o lucro de UM atendimento isolado sem
 * rateio. Só inclui serviços que foram realmente executados (status DONE) no período.
 */
public record ServicePricingAnalysisResponse(
        List<ServicePricingItemResponse> items,
        BigDecimal totalFixedExpenses,
        String period
) {}
