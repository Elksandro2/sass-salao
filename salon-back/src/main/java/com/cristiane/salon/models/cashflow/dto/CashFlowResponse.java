package com.cristiane.salon.models.cashflow.dto;

import com.cristiane.salon.models.cashflow.entity.CashFlow;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CashFlowResponse(
        Long id,
        String type,
        BigDecimal amount,
        String description,
        LocalDate date,
        Long appointmentId,
        Long employeeId,
        String employeeName,
        /** Comissão da venda avulsa de produto, se um vendedor foi informado. */
        BigDecimal commissionAmount
) {
    public static CashFlowResponse fromEntity(CashFlow cashFlow) {
        return new CashFlowResponse(
                cashFlow.getId(),
                cashFlow.getType().name(),
                cashFlow.getAmount(),
                cashFlow.getDescription(),
                cashFlow.getDate(),
                cashFlow.getAppointment() != null ? cashFlow.getAppointment().getId() : null,
                cashFlow.getEmployee() != null ? cashFlow.getEmployee().getId() : null,
                cashFlow.getEmployee() != null ? cashFlow.getEmployee().getUser().getName() : null,
                cashFlow.getCommissionAmount()
        );
    }
}
