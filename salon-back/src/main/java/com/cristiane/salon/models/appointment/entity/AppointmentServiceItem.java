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

    // --- Snapshots: valores do serviço congelados na criação/edição do item, pra que mudanças
    // no cadastro não alterem o financeiro de atendimentos já feitos (ver V72). Nulos em linhas
    // antigas — nesse caso cai no valor atual do serviço. ---

    @Column(name = "snapshot_price", precision = 10, scale = 2)
    private BigDecimal snapshotPrice;

    @Column(name = "snapshot_commission_percent", precision = 5, scale = 2)
    private BigDecimal snapshotCommissionPercent;

    /** Custo total da receita deste serviço no momento do atendimento (soma dos consumos × custo). */
    @Column(name = "snapshot_recipe_cost", precision = 12, scale = 2)
    private BigDecimal snapshotRecipeCost;

    /** customPrice (sobrescrita manual) > snapshot (congelado) > preço atual do serviço. */
    public BigDecimal getEffectivePrice() {
        if (customPrice != null) return customPrice;
        if (snapshotPrice != null) return snapshotPrice;
        return salonService.getPrice();
    }

    /** % de comissão congelado; cai no % atual do serviço se não houver snapshot (linha antiga). */
    public BigDecimal getEffectiveCommissionPercent() {
        return snapshotCommissionPercent != null ? snapshotCommissionPercent : salonService.getCommissionPercent();
    }
}
