package com.cristiane.salon.models.product.dto;

import com.cristiane.salon.models.product.entity.ProductUnit;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record ProductRequest(
        @NotBlank(message = "O nome é obrigatório")
        @Size(min = 3, max = 150, message = "O nome deve ter entre 3 e 150 caracteres")
        String name,

        @NotNull(message = "O preço é obrigatório")
        @DecimalMin(value = "0.00", message = "O preço não pode ser negativo")
        BigDecimal price,

        Boolean active,

        @Size(max = 100, message = "A marca deve ter no máximo 100 caracteres")
        String brand,

        /** Quanto o salão pagou pela embalagem/produto (opcional, usado só pra custeio interno). */
        @DecimalMin(value = "0.00", message = "O custo não pode ser negativo")
        BigDecimal costPrice,

        /** Capacidade da embalagem, na unidade de {@code unit} (ex.: 1000 para 1000ml). */
        @DecimalMin(value = "0.01", message = "A capacidade deve ser maior que zero")
        BigDecimal capacity,

        ProductUnit unit,

        /** Aparece no seletor de venda avulsa (Fluxo de Caixa) e de produtos vendidos no atendimento. */
        Boolean availableForSale,

        /** Aparece no seletor de receita de serviço (quanto o serviço consome). */
        Boolean usedInServiceRecipe
) {}
