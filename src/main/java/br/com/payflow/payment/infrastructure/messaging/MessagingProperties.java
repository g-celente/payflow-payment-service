package br.com.payflow.payment.infrastructure.messaging;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// payflow.messaging.*: Service Bus connection (empty = events wait in the outbox) and the relay schedule.
@ConfigurationProperties("payflow.messaging")
public record MessagingProperties(
        String connectionString,
        @DefaultValue("payment-events") String queue,
        @DefaultValue("true") boolean relayEnabled,
        @DefaultValue("2s") Duration relayInterval,
        @DefaultValue("50") int batchSize) {

    public boolean serviceBusConfigured() {
        return connectionString != null && !connectionString.isBlank();
    }

    @Override
    public String toString() {
        return "MessagingProperties[connectionString=****, queue=" + queue + ", relayEnabled=" + relayEnabled
                + ", relayInterval=" + relayInterval + ", batchSize=" + batchSize + "]";
    }
}
