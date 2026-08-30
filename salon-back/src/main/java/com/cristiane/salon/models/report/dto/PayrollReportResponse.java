package com.cristiane.salon.models.report.dto;

import com.cristiane.salon.models.employee.entity.RemunerationType;
import java.math.BigDecimal;
import java.util.List;

public record PayrollReportResponse(
        List<PayrollItem> items,
        String period
) {
    public record PayrollItem(
            Long employeeId,
            String employeeName,
            RemunerationType remunerationType,
            /** Receita total dos atendimentos concluídos dela no período — contexto, não é mais literalmente "valor × %" (cada serviço tem seu próprio %). */
            BigDecimal baseAmount,
            BigDecimal calculatedPay,
            /** Valor da diária — preenchido só para Diarista/Diária+Comissão, senão null. */
            BigDecimal dailyRate,
            /** Dias trabalhados usados no cálculo — só para Diarista/Diária+Comissão, senão null. */
            Integer daysWorked
    ) {}
}
