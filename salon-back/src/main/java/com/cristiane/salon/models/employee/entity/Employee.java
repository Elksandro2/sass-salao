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

    @Enumerated(EnumType.STRING)
    @Column(name = "remuneration_type")
    private RemunerationType remunerationType;

    /**
     * Salário base — só tem sentido para SALARIO_FIXO e FIXO_E_COMISSIONADO. Para COMISSIONADO
     * fica null: a comissão dela não é mais um % fixo por funcionária, vem do
     * {@code SalonService.commissionPercent} de cada serviço que ela realizar (e da comissão
     * única de produto em {@code SalonBusinessSettings}, que vale pra qualquer tipo de
     * remuneração — inclusive Salário Fixo, como incentivo de venda).
     */
    @Column(name = "remuneration_value", precision = 10, scale = 2)
    private java.math.BigDecimal remunerationValue;

    /**
     * Separa "recebe remuneração via Employee" de "pode ser escalado num agendamento".
     * FUNCIONARIA é sempre bookable; GERENTE_DE_ATENDIMENTO pode ter Employee (pra registrar
     * salário fixo no relatório financeiro) sem nunca aparecer no seletor de atendimento.
     */
    @Column(name = "bookable", nullable = false)
    private boolean bookable = true;
}
