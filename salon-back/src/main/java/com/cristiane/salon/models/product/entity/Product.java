package com.cristiane.salon.models.product.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false)
    private Integer stock = 0;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean active = true;

    // --- Custeio: quanto o salão pagou pelo produto e o quanto rende, pra calcular custo por
    // uso (ver SalonServiceProductUsage) e alimentar os relatórios de lucro por serviço. ---

    @Column(length = 100)
    private String brand;

    /** Quanto o salão pagou por esta embalagem/produto (não é o preço de venda ao cliente). */
    @Column(name = "cost_price", precision = 10, scale = 2)
    private BigDecimal costPrice;

    /** Capacidade da embalagem, na unidade de {@link #unit} (ex.: 1000 para um frasco de 1000ml). */
    @Column(precision = 10, scale = 2)
    private BigDecimal capacity;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ProductUnit unit;

    /** Custo por unidade de {@link #unit} — null se custo ou capacidade não estiverem cadastrados. */
    public BigDecimal getUnitCost() {
        if (costPrice == null || capacity == null || capacity.signum() <= 0) {
            return null;
        }
        return costPrice.divide(capacity, 4, java.math.RoundingMode.HALF_UP);
    }
}
