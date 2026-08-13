package com.cristiane.salon.models.clientanamnesis.entity;

import com.cristiane.salon.models.clientanamnesis.enums.HairType;
import com.cristiane.salon.models.clientanamnesis.enums.SkinType;
import com.cristiane.salon.models.user.entity.User;
import com.cristiane.salon.security.crypto.EncryptedStringConverter;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/**
 * Ficha de anamnese de um cliente — dado de saúde, sensível pela LGPD (Art. 5º, II). Os campos
 * de texto livre (onde a cliente pode escrever qualquer coisa sobre a própria saúde) são
 * cifrados pelo {@link EncryptedStringConverter}; tipo de pele/cabelo ficam em claro por serem
 * categorias fechadas, não identificam nem expõem condição de saúde por si só.
 *
 * <p>Só existe uma ficha por cliente ({@code user_id} único) — o formulário sempre faz upsert,
 * nunca cria um histórico de versões.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_client_anamnesis")
public class ClientAnamnesis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "client_id", nullable = false, unique = true)
    private User client;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "allergies_encrypted", columnDefinition = "TEXT")
    private String allergies;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "health_conditions_encrypted", columnDefinition = "TEXT")
    private String healthConditions;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "medications_encrypted", columnDefinition = "TEXT")
    private String medications;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "additional_notes_encrypted", columnDefinition = "TEXT")
    private String additionalNotes;

    @Enumerated(EnumType.STRING)
    @Column(name = "skin_type", length = 20)
    private SkinType skinType;

    @Enumerated(EnumType.STRING)
    @Column(name = "hair_type", length = 20)
    private HairType hairType;

    /** Quando a cliente consentiu com o registro deste dado de saúde (LGPD, base legal: consentimento). */
    @Column(name = "consent_given_at", nullable = false)
    private Instant consentGivenAt;

    /** Membro da equipe que coletou o consentimento (presencialmente) e registrou a ficha. */
    @Column(name = "consent_given_by_user_id", nullable = false)
    private Long consentGivenByUserId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "updated_by_user_id")
    private Long updatedByUserId;
}
