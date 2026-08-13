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

    public BigDecimal getEffectiveUnitPrice() {
        return customPrice != null ? customPrice : product.getPrice();
    }

    public BigDecimal getEffectiveTotalPrice() {
        return getEffectiveUnitPrice().multiply(BigDecimal.valueOf(quantity));
    }
}
