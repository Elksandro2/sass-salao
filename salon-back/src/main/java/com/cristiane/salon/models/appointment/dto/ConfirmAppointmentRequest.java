package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.enums.AppointmentPeriod;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record ConfirmAppointmentRequest(
        @NotNull(message = "Informe a data confirmada") LocalDate scheduledDate,
        @NotNull(message = "Informe o período confirmado (manhã ou tarde)") AppointmentPeriod scheduledPeriod
) {}
