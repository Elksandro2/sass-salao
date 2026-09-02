package com.cristiane.salon.models.report.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/** Ajuste manual dos dias trabalhados de uma diarista num período da folha. */
public record WorkedDaysOverrideRequest(
        @NotNull(message = "Informe a diarista") Long employeeId,
        @NotNull(message = "Informe o início do período")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodStart,
        @NotNull(message = "Informe o fim do período")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate periodEnd,
        @NotNull(message = "Informe os dias trabalhados")
        @PositiveOrZero(message = "Os dias trabalhados não podem ser negativos") Integer daysWorked
) {
}
