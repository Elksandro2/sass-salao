package com.cristiane.salon.models.appointment.dto;

import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record AppointmentServiceRequest(
        @NotNull(message = "O serviço é obrigatório")
        Long serviceId,

        /**
         * Sobrescreve o preço do serviço só para este agendamento (nulo = usa o valor do
         * catálogo). Só tem efeito no fluxo administrativo — ignorado no fluxo do cliente.
         */
        BigDecimal customPrice,

        /** Observações específicas do serviço customizado para este agendamento (opcional). */
        String customServiceNotes
) {
}
