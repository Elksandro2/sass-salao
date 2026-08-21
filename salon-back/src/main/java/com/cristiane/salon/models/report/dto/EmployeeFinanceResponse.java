package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;

public record EmployeeFinanceResponse(
        Long employeeId,
        String employeeName,
        String remunerationType,
        /** Salário base — só preenchido para SALARIO_FIXO/FIXO_E_COMISSIONADO. */
        BigDecimal remunerationValue,
        long doneAppointmentsCount,
        BigDecimal doneAppointmentsValue,
        BigDecimal doneProductsValue,
        BigDecimal calculatedPayout
) {}
