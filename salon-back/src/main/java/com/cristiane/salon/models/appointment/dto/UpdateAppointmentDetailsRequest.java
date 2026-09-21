package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.enums.AppointmentPeriod;
import java.time.LocalDate;

/**
 * Edição dos dados básicos de um agendamento já criado (profissional, dia/período) — pra
 * corrigir engano de cadastro ou reagendar sem precisar cancelar e criar outro. Todos os campos
 * são opcionais: manda só o que quer trocar. scheduledDate/scheduledPeriod são tudo ou nada —
 * ver validação em AppointmentService.updateDetails.
 */
public record UpdateAppointmentDetailsRequest(
        Long employeeId,
        LocalDate scheduledDate,
        AppointmentPeriod scheduledPeriod
) {
}
