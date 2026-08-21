package com.cristiane.salon.models.service.entity;

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
@Table(name = "tb_salon_service")
public class SalonService {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** Opcional: referência &quot;a partir de&quot;; valor final no caixa */
    @Column(precision = 10, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Boolean active = true;

    /**
     * Comissão (%) paga a qualquer funcionária Comissionada ou Fixo+Comissionada que realizar
     * este serviço — é do serviço, não da funcionária: dois serviços diferentes pagam
     * percentuais diferentes, mas qualquer uma que fizer o mesmo serviço recebe o mesmo %.
     * Null/zero = este serviço não paga comissão de serviço (só a de produto, se vender algo).
     */
    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;
}
