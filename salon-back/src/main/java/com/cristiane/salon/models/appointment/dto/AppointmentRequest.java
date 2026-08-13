package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record AppointmentRequest(
        @NotNull(message = "O funcionário é obrigatório")
        Long employeeId,

        @NotEmpty(message = "Ao menos um serviço é obrigatório")
        List<AppointmentServiceRequest> services,

        /**
         * Produtos vendidos junto com o atendimento (opcional). Só tem efeito no fluxo
         * administrativo (equipe cria o agendamento) — ignorado no fluxo de solicitação do
         * cliente. Também editável depois via {@code PATCH /{id}/products}.
         */
        List<AppointmentProductRequest> products,

        /**
         * Obrigatório apenas no fluxo administrativo (agendamento com horário definido).
         * No fluxo do cliente deve ser omitido/null — o salão confirma o horário depois.
         */
        LocalDateTime scheduledAt,

        /** Cliente indica dia preferido (opcional). */
        LocalDate preferredDate,

        /** Observações do cliente (opcional). */
        @Size(max = 1000, message = "As observações devem ter no máximo 1000 caracteres")
        String clientNotes,

        /** Preenchido apenas quando admin/gerente agenda para um cliente. */
        Long clientId
) {
}
