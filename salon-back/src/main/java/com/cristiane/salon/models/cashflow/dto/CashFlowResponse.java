package com.cristiane.salon.models.cashflow.dto;

import com.cristiane.salon.models.cashflow.entity.CashFlow;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

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
        BigDecimal commissionAmount,
        /** % de comissão congelado na venda. */
        BigDecimal commissionPercent,
        boolean commissionManual,
        /** Itens da venda avulsa (vazio em vendas antigas e em lançamentos que não são venda). */
        List<Item> items
) {
    public record Item(Long productId, String productName, Integer quantity, BigDecimal unitPrice) {}

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
                cashFlow.getCommissionAmount(),
                cashFlow.getCommissionPercent(),
                cashFlow.isCommissionManual(),
                cashFlow.getItems() == null ? List.of() : cashFlow.getItems().stream()
                        .map(i -> new Item(i.getProduct() != null ? i.getProduct().getId() : null,
                                i.getProductName(), i.getQuantity(), i.getUnitPrice()))
                        .toList()
        );
    }
}
