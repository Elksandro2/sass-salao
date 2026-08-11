package com.cristiane.salon.models.employee.entity;

import com.cristiane.salon.models.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_employee")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @Enumerated(EnumType.STRING)
    @Column(name = "remuneration_type")
    private RemunerationType remunerationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "commission_scope")
    private CommissionScope commissionScope;

    @Column(name = "remuneration_value", precision = 10, scale = 2)
    private java.math.BigDecimal remunerationValue;

    @Column(name = "commission_value", precision = 10, scale = 2)
    private java.math.BigDecimal commissionValue;

    /**
     * Separa "recebe remuneração via Employee" de "pode ser escalado num agendamento".
     * FUNCIONARIA é sempre bookable; GERENTE_DE_ATENDIMENTO pode ter Employee (pra registrar
     * salário fixo no relatório financeiro) sem nunca aparecer no seletor de atendimento.
     */
    @Column(name = "bookable", nullable = false)
    private boolean bookable = true;
}
