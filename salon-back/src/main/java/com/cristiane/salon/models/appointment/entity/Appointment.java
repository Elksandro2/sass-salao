package com.cristiane.salon.models.appointment.entity;

import com.cristiane.salon.models.appointment.enums.AppointmentPeriod;
import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
import com.cristiane.salon.models.appointment.enums.PaymentMethod;
import com.cristiane.salon.models.appointment.enums.PaymentStatus;
import com.cristiane.salon.models.employee.entity.Employee;
import com.cristiane.salon.models.user.entity.User;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_appointment")
public class Appointment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AppointmentServiceItem> services = new ArrayList<>();

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AppointmentProductItem> products = new ArrayList<>();

    @OneToMany(mappedBy = "appointment", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<AppointmentExpenseItem> expenses = new ArrayList<>();

    /**
     * Definido pela equipe ao confirmar o pedido do cliente.
     *
     * <p>É {@link LocalDateTime} de propósito, e não {@link Instant}: isto é hora de relógio de
     * parede no endereço do salão. "14h" significa 14h em Recife mesmo que a cliente esteja
     * viajando e mesmo que o servidor rode em outro continente. Guardar como instante faria o
     * horário se deslocar sozinho se a regra de fuso do país mudasse (ver TimeConfig).
     */
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    /**
     * Dia definido pela equipe quando ela agenda por bloco (manhã/tarde) em vez de hora exata —
     * alternativa a {@link #scheduledAt}, nunca os dois preenchidos ao mesmo tempo. Todo
     * agendamento criado antes desta coluna existir usa {@link #scheduledAt}; a equipe não
     * cria mais agendamento com hora exata a partir daqui, só com {@link #scheduledDate} +
     * {@link #scheduledPeriod}. Use {@link #isScheduled()}/{@link #getEffectiveScheduledDate()}
     * em vez de checar estes dois campos direto — cobrem os dois formatos de uma vez.
     */
    @Column(name = "scheduled_date")
    private LocalDate scheduledDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "scheduled_period", length = 10)
    private AppointmentPeriod scheduledPeriod;

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

    /**
     * Preferência de manhã/tarde do cliente pro dia de {@link #preferredDate} — não vinculante,
     * é só um chute de conveniência: quem decide o horário/bloco real é a equipe ao confirmar.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "preferred_period", length = 10)
    private AppointmentPeriod preferredPeriod;

    @Column(name = "client_notes", columnDefinition = "TEXT")
    private String clientNotes;

    /**
     * Observação interna da equipe sobre este atendimento específico — distinta de
     * {@link #clientNotes} (o que o cliente escreveu). Editável a qualquer momento, não só na
     * criação, e fica visível depois no histórico do cliente.
     */
    @Column(name = "internal_notes", columnDefinition = "TEXT")
    private String internalNotes;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_status", length = 20)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;

    /** ID gerado pelo Mercado Pago para rastrear o pagamento via Webhook */
    @Column(name = "payment_id")
    private Long paymentId;

    /**
     * Como o pagamento foi feito. Setado automaticamente como PIX quando confirmado via
     * webhook da plataforma; escolhido manualmente (crédito/débito/pix/dinheiro) quando o
     * admin marca como pago fora da plataforma.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", length = 20)
    private PaymentMethod paymentMethod;

    /** A string "Copia e Cola" do PIX gerada pela API */
    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    /** NULL = ainda não recebeu o lembrete D-1. Marcado no disparo do e-mail, não na
     * confirmação de entrega — a fila de retry do e-mail já cuida disso separadamente. */
    @Column(name = "reminded_at")
    private Instant remindedAt;

    /**
     * % de comissão de produto do salão congelado na criação/edição deste agendamento (ver V72).
     * NULL em agendamentos antigos — nesse caso cai no % atual de SalonBusinessSettings.
     */
    @Column(name = "snapshot_product_commission_percent", precision = 5, scale = 2)
    private BigDecimal snapshotProductCommissionPercent;

    /** Tem uma data (e, se for por bloco, um período) definidos — hora exata (legado) ou
     * manhã/tarde (atual) — que autoriza confirmar/concluir o agendamento. */
    public boolean isScheduled() {
        return scheduledAt != null || (scheduledDate != null && scheduledPeriod != null);
    }

    /** O dia efetivamente marcado, seja pelo formato antigo (hora exata) ou pelo novo (bloco). */
    public LocalDate getEffectiveScheduledDate() {
        if (scheduledAt != null) return scheduledAt.toLocalDate();
        return scheduledDate;
    }

    public BigDecimal getTotalEffectivePrice() {
        return services.stream()
                .map(AppointmentServiceItem::getEffectivePrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public String getServiceNames() {
        return services.stream()
                .map(item -> item.getSalonService().getName())
                .collect(Collectors.joining(", "));
    }

    public BigDecimal getTotalProductsPrice() {
        return products.stream()
                .map(AppointmentProductItem::getEffectiveTotalPrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Base sobre a qual despesas em porcentagem são calculadas: serviços + produtos. */
    public BigDecimal getExpenseBaseAmount() {
        return getTotalEffectivePrice().add(getTotalProductsPrice());
    }

    public BigDecimal getTotalExpensesAmount() {
        BigDecimal base = getExpenseBaseAmount();
        return expenses.stream()
                .map(item -> item.getEffectiveAmount(base))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * Valor final cobrado/recebido pelo agendamento: serviços + produtos - despesas. Quando não
     * há produtos nem despesas cadastrados (caso mais comum hoje), equivale exatamente a
     * {@link #getTotalEffectivePrice()} — mantém compatível o valor cobrado no PIX e faturado
     * no Caixa para todo agendamento que só usa serviços.
     */
    public BigDecimal getGrandTotal() {
        BigDecimal total = getExpenseBaseAmount().subtract(getTotalExpensesAmount());
        return total.max(BigDecimal.ZERO);
    }
}
