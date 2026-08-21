package com.cristiane.salon.models.businesssettings.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Configurações financeiras internas do salão — tabela singleton, só existe UM registro (a
 * aplicação sempre usa o de menor id), igual {@code SalonProfile}, mas ao contrário dela isto
 * NUNCA é exposto publicamente: é informação de negócio (comissão), não de vitrine.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "tb_salon_business_settings")
public class SalonBusinessSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Comissão (%) única, paga a qualquer funcionária que vender qualquer produto — vale pra
     * TODAS, inclusive quem é Salário Fixo (a venda de produto é tratada como incentivo à
     * venda, uma exceção deliberada à regra de que salário fixo não recebe comissão de
     * serviço). Null/zero = comissão de produto desligada.
     */
    @Column(name = "product_commission_percent", precision = 5, scale = 2)
    private BigDecimal productCommissionPercent;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
