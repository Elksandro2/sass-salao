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
 * Exceção à diária automática de uma diarista (Diarista ou Diária + Comissão). Dia com
 * atendimento concluído já conta sozinho com a diária padrão; aqui fica só o que foge disso:
 * dia trabalhado sem atendimento, dia com valor diferente (meio período, feriado...) e o porquê,
 * ou dia com atendimento que foi desmarcado ({@link #removed}).
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

    /** Dia desmarcado de propósito — não conta como diária mesmo tendo atendimento. */
    @Column(nullable = false)
    private boolean removed;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
