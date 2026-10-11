package com.cristiane.salon.models.payroll.entity;

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
 * Um dia trabalhado por uma diarista (Diarista ou Diária + Comissão). Cada dia é um registro
 * próprio — dá pra saber exatamente quais dias foram pagos e, quando a diária daquele dia foi
 * diferente da padrão (meio período, feriado...), o valor e o porquê.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_employee_work_day")
public class EmployeeWorkDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false)
    private Long employeeId;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    /** Valor pago por este dia — nasce com a diária padrão da colaboradora, mas pode ser outro. */
    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal amount;

    /** Por que este dia teve valor diferente da diária padrão (obrigatório nesse caso). */
    @Column(length = 500)
    private String justification;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
