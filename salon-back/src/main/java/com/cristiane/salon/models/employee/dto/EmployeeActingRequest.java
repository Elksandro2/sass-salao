package com.cristiane.salon.models.employee.dto;

import jakarta.validation.constraints.NotNull;

/** Corpo do PUT /v1/employees/me/acting — liga/desliga a atuação do admin em agendamentos. */
public record EmployeeActingRequest(
        @NotNull(message = "Informe se a atuação deve ficar ativa ou não") Boolean acting
) {
}
