package com.cristiane.salon.models.appointment.dto;

import com.cristiane.salon.models.appointment.enums.AppointmentPeriod;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
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
         * Obrigatórios apenas no fluxo administrativo (agendamento já marcado): dia + bloco.
         * A equipe não crava mais hora exata — só manhã ou tarde. No fluxo do cliente devem ser
         * omitidos/null — o salão define isso depois, ao aceitar o pedido.
         */
        LocalDate scheduledDate,
        AppointmentPeriod scheduledPeriod,

        /** Cliente indica dia preferido (opcional). */
        LocalDate preferredDate,

        /** Preferência de manhã/tarde do cliente pro dia acima (opcional, não vinculante — a
         * equipe decide o período real ao aceitar o pedido). */
        AppointmentPeriod preferredPeriod,

        /** Observações do cliente (opcional). */
        @Size(max = 1000, message = "As observações devem ter no máximo 1000 caracteres")
        String clientNotes,

        /** Preenchido apenas quando admin/gerente agenda para um cliente. */
        Long clientId,

        /**
         * Observação interna da equipe (opcional) — mesmo campo editável depois via
         * {@code PATCH /{id}/internal-notes}, só que já disponível na criação. Só tem efeito no
         * fluxo administrativo; ignorado no fluxo de solicitação do cliente.
         */
        @Size(max = 4000, message = "Observações muito longas (máx. 4000 caracteres)")
        String internalNotes
) {
}
