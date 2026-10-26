package br.com.payflow.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "payment")
public class PaymentEntity {

    @Id
    UUID id;

    @Column(name = "merchant_id", nullable = false, updatable = false)
    UUID merchantId;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    long amountCents;

    @Column(nullable = false, updatable = false)
    String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    PaymentMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PaymentStatus status;

    @Column(name = "card_token", updatable = false)
    String cardToken;

    @Column(name = "card_last4", updatable = false)
    String cardLast4;

    @Column(name = "pix_qr_code", updatable = false)
    String pixQrCode;

    @Column(name = "gateway_reference", updatable = false)
    String gatewayReference;

    String description;

    // JSON object of string values.
    String metadata;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    Instant updatedAt;

    @Version
    long version;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    @BatchSize(size = 100)
    List<RefundEntity> refunds = new ArrayList<>();

    protected PaymentEntity() {
    }
}
