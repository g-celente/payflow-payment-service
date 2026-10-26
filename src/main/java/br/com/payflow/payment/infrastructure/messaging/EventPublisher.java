package br.com.payflow.payment.infrastructure.messaging;

import java.util.List;

// Sends outbox messages to the broker; returns only after the broker accepted all of them, or throws.
public interface EventPublisher {

    void publish(List<OutboxMessage> messages);
}
