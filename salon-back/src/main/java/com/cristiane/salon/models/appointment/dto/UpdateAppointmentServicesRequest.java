package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record UpdateAppointmentServicesRequest(
        @NotEmpty(message = "Ao menos um serviço é obrigatório")
        @Valid
        List<AppointmentServiceRequest> services
) {
}
