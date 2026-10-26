package br.com.payflow.payment.infrastructure.persistence;

import java.util.UUID;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentEvent;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static br.com.payflow.payment.support.TestPayments.MERCHANT;
import static br.com.payflow.payment.support.TestPayments.NOW;
import static br.com.payflow.payment.support.TestPayments.authorizedCard;
import static org.assertj.core.api.Assertions.assertThat;

class CloudEventFactoryTests {

    private final JsonMapper json = JsonMapper.builder().build();
    private final CloudEventFactory factory = new CloudEventFactory(json);

    @Test
    void refundEventIsAStructuredCloudEvent() {
        Payment payment = authorizedCard(10000);
        payment.pullEvents();
        payment.refund(UUID.randomUUID(), 2500L, "avaria", NOW);
        PaymentEvent event = payment.pullEvents().getFirst();

        JsonNode cloudEvent = json.readTree(factory.toJson(event));
        assertThat(cloudEvent.get("specversion").asText()).isEqualTo("1.0");
        assertThat(cloudEvent.get("id").asText()).isEqualTo(event.id().toString());
        assertThat(cloudEvent.get("source").asText()).isEqualTo("/payflow/payment-service");
        assertThat(cloudEvent.get("type").asText()).isEqualTo("payment.refunded");
        assertThat(cloudEvent.get("subject").asText()).isEqualTo(payment.id().toString());
        assertThat(cloudEvent.get("time").asText()).isEqualTo("2026-10-26T15:00:00Z");
        assertThat(cloudEvent.get("merchantid").asText()).isEqualTo(MERCHANT.toString());

        JsonNode data = cloudEvent.get("data");
        assertThat(data.get("status").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(data.get("amount").asLong()).isEqualTo(10000);
        assertThat(data.get("refundedAmount").asLong()).isEqualTo(2500);
        assertThat(data.at("/refund/amount").asLong()).isEqualTo(2500);
        assertThat(data.at("/refund/reason").asText()).isEqualTo("avaria");
        assertThat(data.has("cardToken")).isFalse();
    }

    @Test
    void otherEventsHaveNoRefund() {
        PaymentEvent event = authorizedCard(100).pullEvents().getFirst();
        JsonNode data = json.readTree(factory.toJson(event)).get("data");
        assertThat(data.get("status").asText()).isEqualTo("AUTHORIZED");
        assertThat(data.has("refund")).isFalse();
    }
}
