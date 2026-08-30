package com.cristiane.salon.models.appointment.entity;

import com.cristiane.salon.models.product.entity.Product;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Um produto vendido dentro de um agendamento. Segue o mesmo padrão de
 * {@link AppointmentServiceItem}: o cadastro do produto funciona como template, customPrice
 * sobrescreve o preço unitário só para este item.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_appointment_product_item")
public class AppointmentProductItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity = 1;

    @Column(name = "custom_price", precision = 10, scale = 2)
    private BigDecimal customPrice;

    // --- Snapshots: preço de venda e custo unitário do produto congelados na criação/edição do
    // item (ver V72). Nulos em linhas antigas — nesse caso cai no valor atual do produto. ---

    @Column(name = "snapshot_unit_price", precision = 10, scale = 2)
    private BigDecimal snapshotUnitPrice;

    /** Custo da embalagem/produto ({@code Product.costPrice}) congelado — usado no lucro do atendimento. */
    @Column(name = "snapshot_cost_price", precision = 10, scale = 2)
    private BigDecimal snapshotCostPrice;

    /** customPrice (sobrescrita manual) > snapshot (congelado) > preço atual do produto. */
    public BigDecimal getEffectiveUnitPrice() {
        if (customPrice != null) return customPrice;
        if (snapshotUnitPrice != null) return snapshotUnitPrice;
        return product.getPrice();
    }

    public BigDecimal getEffectiveTotalPrice() {
        return getEffectiveUnitPrice().multiply(BigDecimal.valueOf(quantity));
    }

    /** Custo da embalagem congelado; cai no custo atual do produto se não houver snapshot. */
    public BigDecimal getEffectiveCostPrice() {
        return snapshotCostPrice != null ? snapshotCostPrice : product.getCostPrice();
    }
}
