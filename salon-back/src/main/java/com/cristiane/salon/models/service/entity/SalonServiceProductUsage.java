package com.cristiane.salon.models.service.entity;

import com.cristiane.salon.models.product.entity.Product;
import com.cristiane.salon.models.product.entity.ProductUnit;
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

    /** Quantidade consumida por execução, na unidade de {@link #unit} (ex.: 30 = 30ml). */
    @Column(name = "quantity_used", nullable = false, precision = 10, scale = 2)
    private BigDecimal quantityUsed;

    /**
     * Unidade em que {@link #quantityUsed} foi lançado — independente da unidade cadastrada no
     * produto (ex.: produto embalado em Litro, mas a receita consome em ml). Null (linha antiga,
     * de antes desta coluna existir) cai na unidade do próprio produto, mesmo comportamento de
     * sempre. Só pode ser de uma unidade da mesma grandeza da do produto (ver
     * {@link ProductUnit#factorTo}) — não faz sentido lançar "30g" de um produto medido em ml.
     */
    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ProductUnit unit;

    /** Custo estimado desta parte da receita — null se o produto não tem custo/capacidade
     * cadastrados, ou se {@link #unit} não é conversível pra unidade do produto. */
    public BigDecimal getEstimatedCost() {
        BigDecimal unitCost = product.getUnitCost();
        if (unitCost == null) {
            return null;
        }
        // Sem unidade cadastrada de um dos lados (receita antiga, ou produto sem unit) -> não dá
        // pra converter, assume "mesma unidade" (comportamento de sempre antes desta conversão).
        BigDecimal factor = BigDecimal.ONE;
        ProductUnit from = unit != null ? unit : product.getUnit();
        if (from != null && product.getUnit() != null) {
            factor = from.factorTo(product.getUnit());
            if (factor == null) {
                return null; // unidades de grandezas diferentes (ex.: ml pra g)
            }
        }
        return unitCost.multiply(quantityUsed).multiply(factor);
    }
}
