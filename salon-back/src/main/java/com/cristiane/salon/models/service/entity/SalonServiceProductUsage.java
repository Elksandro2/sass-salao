package com.cristiane.salon.models.service.entity;

import com.cristiane.salon.models.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * A "receita" de um serviço: quanto de um produto ele consome por execução (ex.: 30ml de
 * coloração por atendimento de "Coloração"). Alimenta o custo estimado do serviço nos
 * relatórios financeiros — não tem relação com {@code AppointmentProductItem}, que é produto
 * vendido/entregue à cliente, não consumido internamente.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_salon_service_product_usage")
public class SalonServiceProductUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_service_id", nullable = false)
    private SalonService salonService;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    /** Quantidade consumida por execução, na unidade de {@code product.unit} (ex.: 30 = 30ml). */
    @Column(name = "quantity_used", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityUsed;

    /** Custo estimado desta parte da receita — null se o produto não tem custo/capacidade cadastrados. */
    public BigDecimal getEstimatedCost() {
        BigDecimal unitCost = product.getUnitCost();
        if (unitCost == null) {
            return null;
        }
        return unitCost.multiply(quantityUsed);
    }
}
