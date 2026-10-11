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
        Integer daysWorked,
        /** O que a plataforma calcula que ela tem a receber no período filtrado. */
        BigDecimal estimatedPayment,
        /** Pagamentos que cobrem o filtro, com o período real de cada um (contíguos unidos). */
        java.util.List<com.cristiane.salon.models.payroll.dto.PayrollDtos.CoverageRange> paidRanges,
        /** Dias do filtro sem pagamento e com algo a pagar (comissão, diária, lançamento). */
        java.util.List<com.cristiane.salon.models.payroll.dto.PayrollDtos.CoverageRange> unpaidRanges,
        /** Salário fixo de cada mês do filtro: pago (quando) ou pendente. */
        java.util.List<com.cristiane.salon.models.payroll.dto.PayrollDtos.SalaryMonthStatus> salaryMonths
) {}
