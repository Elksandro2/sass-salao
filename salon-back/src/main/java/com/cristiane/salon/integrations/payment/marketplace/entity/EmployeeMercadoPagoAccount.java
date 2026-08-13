package com.cristiane.salon.integrations.payment.marketplace.entity;

import com.cristiane.salon.models.employee.entity.Employee;
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
 * Conexão OAuth de uma funcionária com a própria conta Mercado Pago — usada pro split de
 * pagamento (Fase C): parte do valor do serviço cai direto na conta dela, sem passar pela
 * conta do salão.
 *
 * <p>access_token/refresh_token são cifrados pelo mesmo motivo que CPF/PIX são: dão controle
 * real sobre a conta MP dela, então nunca ficam em texto claro no banco.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "tb_employee_mp_account")
public class EmployeeMercadoPagoAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    /** Identificador público da conta MP dela — não sensível, necessário pra criar o pagamento em nome dela. */
    @Column(name = "mp_user_id", nullable = false, length = 50)
    private String mpUserId;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "access_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String accessToken;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token_encrypted", nullable = false, columnDefinition = "TEXT")
    private String refreshToken;

    @Column(name = "public_key")
    private String publicKey;

    @Column(name = "token_expires_at")
    private Instant tokenExpiresAt;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
