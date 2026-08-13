package com.cristiane.salon.models.clientanamnesis.dto;

import com.cristiane.salon.models.clientanamnesis.entity.ClientAnamnesis;
import com.cristiane.salon.models.clientanamnesis.enums.HairType;
import com.cristiane.salon.models.clientanamnesis.enums.SkinType;

import java.time.Instant;

public record ClientAnamnesisResponse(
        Long id,
        Long clientId,
        String allergies,
        String healthConditions,
        String medications,
        String additionalNotes,
        SkinType skinType,
        HairType hairType,
        Instant consentGivenAt,
        String consentGivenByName,
        Instant createdAt,
        Instant updatedAt,
        String updatedByName
) {
    public static ClientAnamnesisResponse fromEntity(
            ClientAnamnesis entity, String consentGivenByName, String updatedByName) {
        return new ClientAnamnesisResponse(
                entity.getId(),
                entity.getClient().getId(),
                entity.getAllergies(),
                entity.getHealthConditions(),
                entity.getMedications(),
                entity.getAdditionalNotes(),
                entity.getSkinType(),
                entity.getHairType(),
                entity.getConsentGivenAt(),
                consentGivenByName,
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                updatedByName
        );
    }
}
