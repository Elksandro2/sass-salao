package com.cristiane.salon.models.appointment.entity;

import com.cristiane.salon.models.appointment.enums.AppointmentStatus;
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

    @Column(name = "preferred_date")
    private LocalDate preferredDate;

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

    /** A string "Copia e Cola" do PIX gerada pela API */
    @Column(name = "pix_qr_code", columnDefinition = "TEXT")
    private String pixQrCode;

    /** NULL = ainda não recebeu o lembrete D-1. Marcado no disparo do e-mail, não na
     * confirmação de entrega — a fila de retry do e-mail já cuida disso separadamente. */
    @Column(name = "reminded_at")
    private Instant remindedAt;

    public BigDecimal getTotalEffectivePrice() {
        return services.stream()
                .map(AppointmentServiceItem::getEffectivePrice)
                .filter(price -> price != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Integer getTotalEffectiveDurationMin() {
        return services.stream()
                .map(AppointmentServiceItem::getEffectiveDurationMin)
                .filter(duration -> duration != null)
                .reduce(0, Integer::sum);
    }

    public String getServiceNames() {
        return services.stream()
                .map(item -> item.getSalonService().getName())
                .collect(Collectors.joining(", "));
    }
}
