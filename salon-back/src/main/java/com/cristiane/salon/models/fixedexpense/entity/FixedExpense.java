package com.cristiane.salon.models.fixedexpense.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Um gasto fixo/operacional do salão (aluguel, água, luz, salário de funcionária, etc.),
 * lançado livremente pela administração — não vinculado a nenhum atendimento específico.
 *
 * <p>Separado do lançamento livre do Fluxo de Caixa (tb_cashflow) porque a Cristiane pediu uma
 * tela dedicada só pra isso: é a base usada pelo relatório de saúde financeira e pela análise
 * de preço por serviço (ver ReportService), sem se misturar com venda de produto/receita.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_fixed_expense")
public class FixedExpense {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** A que dia/mês esse gasto se refere — usado pra filtrar por período nos relatórios. */
    @Column(nullable = false)
    private LocalDate date;

    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
