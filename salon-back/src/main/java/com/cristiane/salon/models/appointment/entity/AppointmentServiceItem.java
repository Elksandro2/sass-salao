package com.cristiane.salon.models.appointment.entity;

import com.cristiane.salon.models.service.entity.SalonService;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Um serviço dentro de um agendamento (agendamento agora suporta múltiplos serviços).
 * O serviço cadastrado funciona como um template: preço/duração/observações podem ser
 * sobrescritos só para este item, sem alterar o cadastro do serviço.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_appointment_service_item")
public class AppointmentServiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "salon_service_id", nullable = false)
    private SalonService salonService;

    @Column(name = "custom_price", precision = 10, scale = 2)
    private BigDecimal customPrice;

    @Column(name = "custom_service_notes", columnDefinition = "TEXT")
    private String customServiceNotes;

    public BigDecimal getEffectivePrice() {
        return customPrice != null ? customPrice : salonService.getPrice();
    }
}
