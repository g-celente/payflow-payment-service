package br.com.payflow.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "refund")
public class RefundEntity {

    @Id
    UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, updatable = false)
    PaymentEntity payment;

    @Column(name = "amount_cents", nullable = false, updatable = false)
    long amountCents;

    @Column(updatable = false)
    String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    Instant createdAt;

    protected RefundEntity() {
    }
}
