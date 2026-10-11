package com.cristiane.salon.models.payroll.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Salário fixo de um mês quitado dentro de um pagamento da folha. O valor fica congelado: mudar
 * o salário na tela Equipe depois não mexe em mês já pago.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_payroll_salary_payment")
public class PayrollSalaryPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    /** Dia 1 do mês de referência. */
    @Column(name = "salary_month", nullable = false)
    private LocalDate salaryMonth;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;
}
