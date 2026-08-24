package com.cristiane.salon.models.report.dto;

import java.math.BigDecimal;
import java.util.List;

public record FinancialReportResponse(
        BigDecimal totalIncome,
        /** Saídas lançadas no Fluxo de Caixa (livre) — não inclui os Gastos Fixos, contados à parte. */
        BigDecimal totalExpense,
        BigDecimal totalSalaryPaid,
        BigDecimal totalCommissionPaid,
        /** Aluguel, água, luz, etc. — tela dedicada de Gastos Fixos, somada aqui pro lucro líquido ficar completo. */
        BigDecimal totalFixedExpenses,
        BigDecimal netProfit,
        List<EmployeeFinanceResponse> employeeFinanceDetails,
        String period
) {}
