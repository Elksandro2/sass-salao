package com.cristiane.salon.models.payroll.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Uma diária que conta na folha: ou um registro salvo ({@code id} preenchido), ou um dia com
 * atendimento concluído que entrou automaticamente com a diária padrão ({@code automatic}, sem id).
 */
public record WorkDayEntry(Long id, LocalDate date, BigDecimal amount, String justification, boolean automatic) {}
