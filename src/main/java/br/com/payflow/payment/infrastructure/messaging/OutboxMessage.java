package br.com.payflow.payment.infrastructure.messaging;

import java.util.UUID;

// One outbox_event row: payload is the CloudEvent JSON, id is also the CloudEvent id.
public record OutboxMessage(UUID id, String type, UUID aggregateId, String payload) {
}
