package com.cristiane.salon.models.service.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record SalonServiceRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(max = 150, message = "O nome deve ter no máximo 150 caracteres")
        String name,

        @Size(max = 2000, message = "A descrição deve ter no máximo 2000 caracteres")
        String description,

        /** Opcional: exibido como &quot;a partir de&quot; no site */
        @Min(value = 0, message = "O preço não pode ser negativo")
        BigDecimal price,

        Boolean active,

        /** Receita: quanto de cada produto este serviço consome por execução (opcional). */
        @Valid
        List<ServiceProductUsageRequest> productUsages
) {}
