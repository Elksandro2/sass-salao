package com.cristiane.salon.models.cashflow.entity;

import com.cristiane.salon.models.appointment.entity.Appointment;
import com.cristiane.salon.models.cashflow.enums.CashFlowType;
import com.cristiane.salon.models.employee.entity.Employee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_cashflow")
public class CashFlow {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private CashFlowType type;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private LocalDate date;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id")
    private Appointment appointment;

    /** Quem vendeu, numa venda avulsa de produto (fora de um atendimento) — opcional. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id")
    private Employee employee;

    /**
     * Comissão da venda avulsa, calculada e congelada no momento da venda a partir do
     * percentual de comissão sobre produtos da funcionária — não recalcula se o percentual
     * dela mudar depois, pra não alterar um lançamento financeiro já fechado.
     */
    @Column(name = "commission_amount", precision = 10, scale = 2)
    private BigDecimal commissionAmount;

    /** % de comissão de produto vigente na venda (ver V87) — usado pra recalcular ao editar. */
    @Column(name = "commission_percent", precision = 5, scale = 2)
    private BigDecimal commissionPercent;

    /** Comissão digitada à mão na edição da venda: editar de novo não recalcula por cima. */
    @Column(name = "commission_manual", nullable = false)
    private boolean commissionManual;

    /**
     * Comissão que a vendedora recebe por esta venda avulsa. Digitada à mão vence tudo; senão é
     * o total × % congelado na venda. Venda feita quando a comissão ainda não estava configurada
     * (sem %) usa o % atual do salão — não fica de fora da folha pra sempre.
     */
    public BigDecimal effectiveCommission(BigDecimal currentPercent) {
        if (employee == null) return null;
        if (commissionManual) return commissionAmount;
        boolean percentFrozen = commissionPercent != null && commissionPercent.signum() > 0;
        // Comissão já gravada (inclusive vendas antigas, que gravavam o valor mas não o %) vale.
        // Só recalcula quando está vazia ou zerada por falta de % configurado na época.
        if (commissionAmount != null && (commissionAmount.signum() > 0 || percentFrozen)) {
            return commissionAmount;
        }
        BigDecimal pct = percentFrozen ? commissionPercent : currentPercent;
        if (pct == null || pct.signum() <= 0 || amount == null) return null;
        return amount.multiply(pct).divide(new BigDecimal("100"), 2, java.math.RoundingMode.HALF_UP);
    }

    /** Itens da venda avulsa (vendas anteriores à V87 não têm — só o total). */
    @OneToMany(mappedBy = "cashFlow", cascade = CascadeType.ALL, orphanRemoval = true)
    private java.util.List<CashFlowItem> items = new java.util.ArrayList<>();
}
