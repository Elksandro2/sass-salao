package com.cristiane.salon.models.clientanamnesis.dto;

import com.cristiane.salon.models.clientanamnesis.enums.HairType;
import com.cristiane.salon.models.clientanamnesis.enums.SkinType;
import jakarta.validation.constraints.Size;

public record ClientAnamnesisRequest(
        @Size(max = 4000, message = "As alergias devem ter no máximo 4000 caracteres")
        String allergies,

        @Size(max = 4000, message = "As condições de saúde devem ter no máximo 4000 caracteres")
        String healthConditions,

        @Size(max = 4000, message = "Os medicamentos devem ter no máximo 4000 caracteres")
        String medications,

        @Size(max = 4000, message = "As observações devem ter no máximo 4000 caracteres")
        String additionalNotes,

        SkinType skinType,

        HairType hairType,

        /** A cliente precisa consentir com o registro deste dado de saúde (LGPD) — validado em
         * {@link com.cristiane.salon.models.clientanamnesis.service.ClientAnamnesisService}. */
        boolean consentGiven
) {
}
