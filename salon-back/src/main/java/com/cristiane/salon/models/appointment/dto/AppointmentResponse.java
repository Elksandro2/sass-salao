package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.entity.Appointment;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

public record AppointmentResponse(
        Long id,
        Long clientId,
        String clientName,
        Long employeeId,
        String employeeName,
        List<AppointmentServiceResponse> services,
        List<AppointmentProductResponse> products,
        List<AppointmentExpenseResponse> expenses,
        BigDecimal totalPrice,
        Integer totalDurationMin,
        BigDecimal totalProductsPrice,
        BigDecimal totalExpensesAmount,
        BigDecimal grandTotal,
        LocalDateTime scheduledAt,
        LocalDate preferredDate,
        String clientNotes,
        String internalNotes,
        String status,
        String paymentStatus,
        Long paymentId,
        String pixQrCode,
        Boolean clientHasSavedCpf,
        String clientCpfMasked
) {
    public static AppointmentResponse fromEntity(Appointment appointment) {
        String rawCpf = appointment.getClient().getCpf();
        boolean hasSavedCpf = rawCpf != null && !rawCpf.isBlank();
        String maskedCpf = "";
        if (hasSavedCpf) {
            String clean = rawCpf.replaceAll("\\D", "");
            if (clean.length() == 11) {
                maskedCpf = "***.***." + clean.substring(6, 9) + "-";
            } else {
                maskedCpf = rawCpf;
            }
        }

        List<AppointmentServiceResponse> services = appointment.getServices().stream()
                .map(AppointmentServiceResponse::fromEntity)
                .collect(Collectors.toList());

        List<AppointmentProductResponse> products = appointment.getProducts().stream()
                .map(AppointmentProductResponse::fromEntity)
                .collect(Collectors.toList());

        BigDecimal expenseBase = appointment.getExpenseBaseAmount();
        List<AppointmentExpenseResponse> expenses = appointment.getExpenses().stream()
                .map(item -> AppointmentExpenseResponse.fromEntity(item, expenseBase))
                .collect(Collectors.toList());

        return new AppointmentResponse(
                appointment.getId(),
                appointment.getClient().getId(),
                appointment.getClient().getName(),
                appointment.getEmployee().getId(),
                appointment.getEmployee().getUser().getName(),
                services,
                products,
                expenses,
                appointment.getTotalEffectivePrice(),
                appointment.getTotalEffectiveDurationMin(),
                appointment.getTotalProductsPrice(),
                appointment.getTotalExpensesAmount(),
                appointment.getGrandTotal(),
                appointment.getScheduledAt(),
                appointment.getPreferredDate(),
                appointment.getClientNotes(),
                appointment.getInternalNotes(),
                appointment.getStatus().name(),
                appointment.getPaymentStatus() != null ? appointment.getPaymentStatus().name() : null,
                appointment.getPaymentId(),
                appointment.getPixQrCode(),
                hasSavedCpf,
                maskedCpf
        );
    }
}
