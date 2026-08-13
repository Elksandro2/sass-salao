package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAppointmentProductsRequest(
        @NotNull(message = "A lista de produtos é obrigatória (pode ser vazia)")
        @Valid
        List<AppointmentProductRequest> products
) {
}
