package com.cristiane.salon.models.generalnote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GeneralNoteRequest(
        @NotBlank(message = "O conteúdo da anotação é obrigatório")
        @Size(max = 4000, message = "Anotação muito longa (máx. 4000 caracteres)")
        String content
) {
}
