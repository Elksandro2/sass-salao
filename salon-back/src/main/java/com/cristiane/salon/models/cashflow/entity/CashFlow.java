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

    @Column(nullable = false, length = 255)
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
}
