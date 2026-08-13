package com.cristiane.salon.models.appointment.entity;

import com.cristiane.salon.models.appointment.enums.ExpenseValueType;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Uma despesa lançada num agendamento específico (ex.: material extra usado no atendimento).
 * Pode ser um valor fixo em R$ ou uma porcentagem sobre o total de serviços+produtos do
 * agendamento — ver {@link Appointment#getTotalExpensesAmount()}.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_appointment_expense_item")
public class AppointmentExpenseItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @Column(nullable = false, length = 200)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 20)
    private ExpenseValueType valueType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal value;

    public BigDecimal getEffectiveAmount(BigDecimal baseAmount) {
        if (valueType == ExpenseValueType.FIXED) {
            return value;
        }
        return baseAmount.multiply(value).divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
    }
}
