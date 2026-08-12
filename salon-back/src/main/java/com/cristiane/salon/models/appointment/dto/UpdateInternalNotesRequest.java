package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.constraints.Size;

public record UpdateInternalNotesRequest(
        @Size(max = 4000, message = "Observações muito longas (máx. 4000 caracteres)")
        String internalNotes
) {
}
