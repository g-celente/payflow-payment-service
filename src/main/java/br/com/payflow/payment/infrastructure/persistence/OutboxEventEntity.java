package br.com.payflow.payment.infrastructure.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// Written here in the payment's transaction; read and marked published by the OutboxRelay (plain JDBC).
@Entity
@Table(name = "outbox_event")
public class OutboxEventEntity {

    @Id
    UUID id;

    @Column(name = "aggregate_id", nullable = false, updatable = false)
    UUID aggregateId;

    @Column(nullable = false, updatable = false)
    String type;

    // Full CloudEvents 1.0 JSON, sent as is.
    @Column(nullable = false, updatable = false)
    String payload;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    Instant occurredAt;

    @Column(name = "published_at")
    Instant publishedAt;

    protected OutboxEventEntity() {
    }

    OutboxEventEntity(UUID id, UUID aggregateId, String type, String payload, Instant occurredAt) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.type = type;
        this.payload = payload;
        this.occurredAt = occurredAt;
    }
}
