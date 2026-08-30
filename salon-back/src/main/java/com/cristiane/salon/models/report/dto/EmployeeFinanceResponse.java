package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;

public record EmployeeFinanceResponse(
        Long employeeId,
        String employeeName,
        String remunerationType,
        /** Salário base (fixo) ou valor da diária (diarista). Não se aplica a COMISSIONADO. */
        BigDecimal remunerationValue,
        long doneAppointmentsCount,
        BigDecimal doneAppointmentsValue,
        BigDecimal doneProductsValue,
        BigDecimal calculatedPayout,
        /** Dias trabalhados no período — só para Diarista/Diária+Comissão, senão null. */
        Integer daysWorked
) {}
