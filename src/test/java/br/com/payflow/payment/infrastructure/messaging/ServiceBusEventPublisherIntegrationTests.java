package br.com.payflow.payment.infrastructure.messaging;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import org.junit.jupiter.api.Test;
import org.testcontainers.azure.ServiceBusEmulatorContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.MountableFile;

import static org.assertj.core.api.Assertions.assertThat;

// The real adapter against the official Service Bus emulator (same queue settings as docker/servicebus/Config.json).
@Testcontainers
class ServiceBusEventPublisherIntegrationTests {

    private static final Network NETWORK = Network.newNetwork();

    private static final MSSQLServerContainer SQL = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense()
            .withNetwork(NETWORK);

    @Container
    static final ServiceBusEmulatorContainer EMULATOR =
            new ServiceBusEmulatorContainer("mcr.microsoft.com/azure-messaging/servicebus-emulator:latest")
                    .acceptLicense()
                    .withConfig(MountableFile.forClasspathResource("servicebus-emulator-config.json"))
                    .withNetwork(NETWORK)
                    .withMsSqlServerContainer(SQL);

    @Test
    void publishesStructuredCloudEventsWithTheirIdAsMessageId() {
        var builder = new ServiceBusClientBuilder().connectionString(EMULATOR.getConnectionString());
        var first = new OutboxMessage(UUID.randomUUID(), "payment.authorized", UUID.randomUUID(),
                "{\"specversion\":\"1.0\",\"type\":\"payment.authorized\"}");
        var second = new OutboxMessage(UUID.randomUUID(), "payment.refunded", UUID.randomUUID(),
                "{\"specversion\":\"1.0\",\"type\":\"payment.refunded\"}");

        try (var publisher = new ServiceBusEventPublisher(builder.sender().queueName("payment-events").buildClient());
             var receiver = builder.receiver().queueName("payment-events").buildClient()) {
            publisher.publish(List.of(first, second));

            List<ServiceBusReceivedMessage> received = new ArrayList<>();
            for (int attempt = 0; attempt < 5 && received.size() < 2; attempt++) {
                receiver.receiveMessages(2 - received.size(), Duration.ofSeconds(10)).forEach(received::add);
            }
            assertThat(received).extracting(ServiceBusReceivedMessage::getMessageId)
                    .containsExactly(first.id().toString(), second.id().toString());
            ServiceBusReceivedMessage message = received.getFirst();
            assertThat(message.getSubject()).isEqualTo("payment.authorized");
            assertThat(message.getContentType()).isEqualTo("application/cloudevents+json; charset=utf-8");
            assertThat(message.getBody().toString()).isEqualTo(first.payload());
            assertThat(message.getApplicationProperties()).containsEntry("paymentId", first.aggregateId().toString());
            received.forEach(receiver::complete);
        }
    }
}
