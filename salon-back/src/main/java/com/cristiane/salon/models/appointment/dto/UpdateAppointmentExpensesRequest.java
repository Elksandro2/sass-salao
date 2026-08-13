package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UpdateAppointmentExpensesRequest(
        @NotNull(message = "A lista de despesas é obrigatória (pode ser vazia)")
        @Valid
        List<AppointmentExpenseRequest> expenses
) {
}
