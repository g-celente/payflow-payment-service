package br.com.payflow.payment.infrastructure.messaging;

import java.util.List;

import com.azure.core.util.BinaryData;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;

// CloudEvents in structured mode on the payment-events queue. messageId = event id, so the queue's duplicate
// detection (when enabled) and the consumers can drop repeats.
public final class ServiceBusEventPublisher implements EventPublisher, AutoCloseable {

    static final String CONTENT_TYPE = "application/cloudevents+json; charset=utf-8";

    private final ServiceBusSenderClient sender;

    public ServiceBusEventPublisher(ServiceBusSenderClient sender) {
        this.sender = sender;
    }

    @Override
    public void publish(List<OutboxMessage> messages) {
        sender.sendMessages(messages.stream().map(ServiceBusEventPublisher::toMessage).toList());
    }

    static ServiceBusMessage toMessage(OutboxMessage outbox) {
        ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromString(outbox.payload()));
        message.setMessageId(outbox.id().toString());
        message.setContentType(CONTENT_TYPE);
        message.setSubject(outbox.type());
        message.getApplicationProperties().put("cloudEvents_type", outbox.type());
        message.getApplicationProperties().put("paymentId", outbox.aggregateId().toString());
        return message;
    }

    @Override
    public void close() {
        sender.close();
    }
}
