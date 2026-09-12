package com.cristiane.salon.models.product.dto;

public record ProductFilter(
    String name,
    Boolean active,
    /** Filtra a tela "Produtos (Venda)". */
    Boolean availableForSale,
    /** Filtra a tela "Produtos (Uso)". */
    Boolean usedInServiceRecipe
) {}
