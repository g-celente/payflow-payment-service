package br.com.payflow.payment.infrastructure.messaging;

import java.time.Clock;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(MessagingProperties.class)
public class MessagingConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MessagingConfiguration.class);

    // Real broker only when SERVICEBUS_CONNECTION_STRING is set (cloud namespace or the local emulator).
    @Bean(destroyMethod = "close")
    @ConditionalOnExpression("!'${payflow.messaging.connection-string:}'.isBlank()")
    ServiceBusEventPublisher serviceBusEventPublisher(MessagingProperties properties) {
        return new ServiceBusEventPublisher(new ServiceBusClientBuilder()
                .connectionString(properties.connectionString())
                .sender()
                .queueName(properties.queue())
                .buildClient());
    }

    @Bean
    OutboxRelay outboxRelay(JdbcTemplate jdbc, PlatformTransactionManager transactionManager,
            ObjectProvider<EventPublisher> publisher, MessagingProperties properties, Clock clock) {
        EventPublisher available = publisher.getIfAvailable();
        if (available == null) {
            log.warn("Service Bus not configured: payment events stay in outbox_event until SERVICEBUS_CONNECTION_STRING is set");
            available = messages -> {
                throw new IllegalStateException("Service Bus is not configured");
            };
        }
        return new OutboxRelay(jdbc, new TransactionTemplate(transactionManager), available, properties.batchSize(), clock);
    }

    @Bean
    @ConditionalOnProperty(name = "payflow.messaging.relay-enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("!'${payflow.messaging.connection-string:}'.isBlank()")
    OutboxRelayScheduler outboxRelayScheduler(OutboxRelay relay) {
        return new OutboxRelayScheduler(relay);
    }

    static final class OutboxRelayScheduler {

        private final OutboxRelay relay;

        OutboxRelayScheduler(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${payflow.messaging.relay-interval:2s}")
        void publish() {
            try {
                int published = relay.publishAllPending();
                if (published > 0) {
                    log.debug("Published {} payment events", published);
                }
            } catch (RuntimeException exception) {
                log.warn("Outbox relay failed, events stay pending and are retried: {}", exception.getMessage());
            }
        }
    }
}
