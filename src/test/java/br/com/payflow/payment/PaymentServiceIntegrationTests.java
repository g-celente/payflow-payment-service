package br.com.payflow.payment;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import br.com.payflow.payment.infrastructure.messaging.EventPublisher;
import br.com.payflow.payment.infrastructure.messaging.OutboxMessage;
import br.com.payflow.payment.infrastructure.messaging.OutboxRelay;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Real HTTP against a real SQL Server 2022 (Testcontainers) with the Flyway schema. The broker is replaced by a
// recording publisher here; ServiceBusEventPublisherIntegrationTests covers the real Service Bus (emulator).
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(PaymentServiceIntegrationTests.RecordingPublisherConfiguration.class)
class PaymentServiceIntegrationTests {

    private static final String INTERNAL_KEY = "internal-" + UUID.randomUUID();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Container
    static final MSSQLServerContainer SQL = new MSSQLServerContainer("mcr.microsoft.com/mssql/server:2022-latest")
            .acceptLicense();

    @DynamicPropertySource
    static void configure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", SQL::getJdbcUrl);
        registry.add("spring.datasource.username", SQL::getUsername);
        registry.add("spring.datasource.password", SQL::getPassword);
        registry.add("payflow.internal-key", () -> INTERNAL_KEY);
        registry.add("payflow.gateway.timeout", () -> "300ms");
        registry.add("payflow.gateway.simulated-delay", () -> "1500ms");
    }

    @Autowired
    Environment environment;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    OutboxRelay relay;

    @Autowired
    RecordingEventPublisher publisher;

    private final UUID merchant = UUID.randomUUID();

    // ---- card ----------------------------------------------------------------------------------------------

    @Test
    void cardPaymentIsAuthorizedAndRefundedInSteps() throws Exception {
        var created = create(Map.of("amount", 10000, "method", "CARD", "cardToken", "tok_visa_1234",
                "description", "Pedido 42", "metadata", Map.of("order_id", "42")));
        assertThat(created.statusCode()).isEqualTo(201);
        JsonNode payment = JSON.readTree(created.body());
        String id = payment.get("id").asText();
        assertThat(created.headers().firstValue("Location")).hasValue("/v1/payments/" + id);
        assertThat(payment.get("status").asText()).isEqualTo("AUTHORIZED");
        assertThat(payment.get("currency").asText()).isEqualTo("BRL");
        assertThat(payment.at("/card/last4").asText()).isEqualTo("1234");
        assertThat(payment.at("/metadata/order_id").asText()).isEqualTo("42");
        assertThat(created.body()).doesNotContain("tok_visa_1234");

        var partial = call("POST", "/v1/payments/" + id + "/refunds", "{\"amount\":2500,\"reason\":\"avaria\"}", key());
        assertThat(partial.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(partial.body()).get("status").asText()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(JSON.readTree(partial.body()).get("refundedAmount").asLong()).isEqualTo(2500);

        var tooMuch = call("POST", "/v1/payments/" + id + "/refunds", "{\"amount\":7501}", key());
        assertThat(tooMuch.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(tooMuch.body()).get("detail").asText()).contains("7500");

        var rest = call("POST", "/v1/payments/" + id + "/refunds", null, key());
        assertThat(rest.statusCode()).isEqualTo(201);
        JsonNode refunded = JSON.readTree(rest.body());
        assertThat(refunded.get("status").asText()).isEqualTo("REFUNDED");
        assertThat(refunded.get("refunds").size()).isEqualTo(2);
        assertThat(refunded.at("/refunds/0/reason").asText()).isEqualTo("avaria");

        assertThat(call("POST", "/v1/payments/" + id + "/refunds", null, key()).statusCode()).isEqualTo(409);
        assertThat(call("DELETE", "/v1/payments/" + id, null, null).statusCode()).isEqualTo(409);

        JsonNode fetched = JSON.readTree(call("GET", "/v1/payments/" + id, null, null).body());
        assertThat(fetched.get("status").asText()).isEqualTo("REFUNDED");
        assertThat(fetched.get("refunds").size()).isEqualTo(2);
    }

    @Test
    void declinedCardIsRecorded() throws Exception {
        var declined = create(Map.of("amount", 5000, "method", "CARD", "cardToken", "tok_declined"));
        assertThat(declined.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(declined.body()).get("status").asText()).isEqualTo("DECLINED");
    }

    @Test
    void gatewayTimeoutRecordsNothingAndTheSameKeyCanBeRetried() throws Exception {
        String idempotencyKey = key();
        String body = JSON.writeValueAsString(Map.of("amount", 1000, "method", "CARD", "cardToken", "tok_timeout"));
        for (int attempt = 0; attempt < 2; attempt++) {
            var response = call("POST", "/v1/payments", body, idempotencyKey);
            assertThat(response.statusCode()).isEqualTo(504);
            assertThat(JSON.readTree(response.body()).get("detail").asText()).contains("retry");
        }
        assertThat(paymentsOfMerchant()).isZero();
    }

    // ---- PIX -----------------------------------------------------------------------------------------------

    @Test
    void pixIsPendingUntilConfirmedAndCanBeCanceledBefore() throws Exception {
        JsonNode pix = JSON.readTree(create(Map.of("amount", 3000, "method", "PIX")).body());
        assertThat(pix.get("status").asText()).isEqualTo("PENDING");
        assertThat(pix.at("/pix/qrCode").asText()).startsWith("000201").contains("br.gov.bcb.pix");
        String id = pix.get("id").asText();

        var confirmed = call("POST", "/v1/payments/" + id + "/pix-confirmation", null, null);
        assertThat(confirmed.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(confirmed.body()).get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(call("POST", "/v1/payments/" + id + "/pix-confirmation", null, null).statusCode()).isEqualTo(409);

        String other = JSON.readTree(create(Map.of("amount", 3000, "method", "PIX")).body()).get("id").asText();
        var canceled = call("DELETE", "/v1/payments/" + other, null, null);
        assertThat(canceled.statusCode()).isEqualTo(200);
        assertThat(JSON.readTree(canceled.body()).get("status").asText()).isEqualTo("CANCELED");
        assertThat(call("GET", "/v1/payments/" + other, null, null).statusCode()).isEqualTo(200);
    }

    // ---- idempotency ---------------------------------------------------------------------------------------

    @Test
    void repeatedRequestReturnsTheFirstAnswer() throws Exception {
        String idempotencyKey = key();
        String body = JSON.writeValueAsString(Map.of("amount", 1990, "method", "CARD", "cardToken", "tok_approved"));
        var first = call("POST", "/v1/payments", body, idempotencyKey);
        var second = call("POST", "/v1/payments", body, idempotencyKey);
        assertThat(second.statusCode()).isEqualTo(201);
        assertThat(JSON.readTree(second.body()).get("id").asText()).isEqualTo(JSON.readTree(first.body()).get("id").asText());
        assertThat(second.headers().firstValue("Idempotent-Replayed")).hasValue("true");
        assertThat(paymentsOfMerchant()).isEqualTo(1);

        String otherBody = JSON.writeValueAsString(Map.of("amount", 2000, "method", "CARD", "cardToken", "tok_approved"));
        assertThat(call("POST", "/v1/payments", otherBody, idempotencyKey).statusCode()).isEqualTo(422);
        assertThat(call("POST", "/v1/payments", body, null).statusCode()).isEqualTo(400);
        assertThat(paymentsOfMerchant()).isEqualTo(1);
    }

    @Test
    void concurrentRequestsWithTheSameKeyCreateOnePayment() throws Exception {
        String idempotencyKey = key();
        String body = JSON.writeValueAsString(Map.of("amount", 1990, "method", "CARD", "cardToken", "tok_approved"));
        List<Integer> statuses = Collections.synchronizedList(new ArrayList<>());
        try (var executor = Executors.newFixedThreadPool(6)) {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int index = 0; index < 6; index++) {
                tasks.add(() -> {
                    statuses.add(call("POST", "/v1/payments", body, idempotencyKey).statusCode());
                    return null;
                });
            }
            for (var result : executor.invokeAll(tasks)) {
                result.get();
            }
        }
        assertThat(statuses).allMatch(status -> status == 201 || status == 409).contains(201);
        assertThat(paymentsOfMerchant()).isEqualTo(1);
    }

    // ---- merchant isolation, listing and summary -----------------------------------------------------------

    @Test
    void otherMerchantsCannotSeeOrChangeThePayment() throws Exception {
        String id = JSON.readTree(create(Map.of("amount", 1000, "method", "CARD", "cardToken", "tok_approved")).body())
                .get("id").asText();
        UUID stranger = UUID.randomUUID();
        assertThat(call(stranger, "GET", "/v1/payments/" + id, null, null).statusCode()).isEqualTo(404);
        assertThat(call(stranger, "POST", "/v1/payments/" + id + "/refunds", null, key()).statusCode()).isEqualTo(404);
        assertThat(JSON.readTree(call(stranger, "GET", "/v1/payments", null, null).body()).get("totalItems").asLong()).isZero();
    }

    @Test
    void listFiltersPagesAndSummaryAggregatesPerMerchant() throws Exception {
        create(Map.of("amount", 10000, "method", "CARD", "cardToken", "tok_approved"));
        create(Map.of("amount", 5000, "method", "CARD", "cardToken", "tok_declined"));
        create(Map.of("amount", 3000, "method", "PIX"));
        String confirmed = JSON.readTree(create(Map.of("amount", 2000, "method", "PIX")).body()).get("id").asText();
        call("POST", "/v1/payments/" + confirmed + "/pix-confirmation", null, null);
        call("POST", "/v1/payments/" + confirmed + "/refunds", "{\"amount\":500}", key());

        JsonNode all = JSON.readTree(call("GET", "/v1/payments?size=3", null, null).body());
        assertThat(all.get("totalItems").asLong()).isEqualTo(4);
        assertThat(all.get("totalPages").asLong()).isEqualTo(2);
        assertThat(all.get("items").size()).isEqualTo(3);
        assertThat(all.at("/items/0/id").asText()).as("newest first").isEqualTo(confirmed);
        assertThat(JSON.readTree(call("GET", "/v1/payments?status=PENDING", null, null).body()).get("totalItems").asLong()).isEqualTo(1);
        assertThat(JSON.readTree(call("GET", "/v1/payments?method=CARD", null, null).body()).get("totalItems").asLong()).isEqualTo(2);
        assertThat(JSON.readTree(call("GET", "/v1/payments?from=2000-01-01T00:00:00Z&to=2001-01-01T00:00:00Z", null, null)
                .body()).get("totalItems").asLong()).isZero();

        JsonNode summary = JSON.readTree(call("GET", "/v1/payments/summary", null, null).body());
        assertThat(summary.get("count").asLong()).isEqualTo(4);
        assertThat(summary.get("currency").asText()).isEqualTo("BRL");
        assertThat(summary.get("totalAmount").asLong()).isEqualTo(10000 + 2000 - 500);
        assertThat(summary.at("/byMethod/CARD").asLong()).isEqualTo(10000);
        assertThat(summary.at("/byMethod/PIX").asLong()).isEqualTo(1500);
        assertThat(summary.at("/byStatus/AUTHORIZED").asLong()).isEqualTo(1);
        assertThat(summary.at("/byStatus/DECLINED").asLong()).isEqualTo(1);
        assertThat(summary.at("/byStatus/PENDING").asLong()).isEqualTo(1);
        assertThat(summary.at("/byStatus/PARTIALLY_REFUNDED").asLong()).isEqualTo(1);
        assertThat(summary.at("/byStatus/REFUNDED").asLong()).isZero();
    }

    @Test
    void patchChangesDescriptionAndMetadataOnly() throws Exception {
        String id = JSON.readTree(create(Map.of("amount", 1000, "method", "CARD", "cardToken", "tok_approved",
                "description", "Pedido 1")).body()).get("id").asText();
        var patched = call("PATCH", "/v1/payments/" + id, "{\"metadata\":{\"order_id\":\"99\"}}", null);
        assertThat(patched.statusCode()).isEqualTo(200);
        JsonNode body = JSON.readTree(patched.body());
        assertThat(body.get("description").asText()).isEqualTo("Pedido 1");
        assertThat(body.at("/metadata/order_id").asText()).isEqualTo("99");
        assertThat(body.get("status").asText()).isEqualTo("AUTHORIZED");
    }

    // ---- events --------------------------------------------------------------------------------------------

    @Test
    void eventsAreWrittenWithThePaymentAndPublishedByTheRelay() throws Exception {
        String id = JSON.readTree(create(Map.of("amount", 1000, "method", "CARD", "cardToken", "tok_approved")).body())
                .get("id").asText();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class, id)).isEqualTo(1);

        publisher.failNext();
        assertThatThrownBy(() -> relay.publishAllPending()).hasMessageContaining("broker down");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class, id)).as("a broker failure keeps the event pending").isEqualTo(1);

        relay.publishAllPending();
        OutboxMessage message = publisher.published().stream()
                .filter(published -> published.aggregateId().toString().equals(id)).findFirst().orElseThrow();
        assertThat(message.type()).isEqualTo("payment.authorized");
        JsonNode cloudEvent = JSON.readTree(message.payload());
        assertThat(cloudEvent.get("id").asText()).isEqualTo(message.id().toString());
        assertThat(cloudEvent.at("/data/paymentId").asText()).isEqualTo(id);
        assertThat(cloudEvent.get("merchantid").asText()).isEqualTo(merchant.toString());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM outbox_event WHERE aggregate_id = ? AND published_at IS NULL",
                Integer.class, id)).isZero();
    }

    // ---- platform rules and operations ---------------------------------------------------------------------

    @Test
    void onlyTheBffWithAMerchantIsServed() throws Exception {
        var noKey = send("GET", "/v1/payments", null, Map.of("X-Merchant-Id", merchant.toString()));
        assertThat(noKey.statusCode()).isEqualTo(401);
        assertThat(noKey.headers().firstValue("WWW-Authenticate")).hasValue("ApiKey header=\"X-Internal-Key\"");
        assertThat(send("GET", "/v1/payments", null, Map.of("X-Internal-Key", INTERNAL_KEY)).statusCode()).isEqualTo(400);
    }

    @Test
    void invalidInputIsBadRequestWithTheReason() throws Exception {
        var zero = create(Map.of("amount", 0, "method", "CARD", "cardToken", "tok_approved"));
        assertThat(zero.statusCode()).isEqualTo(400);
        assertThat(JSON.readTree(zero.body()).get("detail").asText()).contains("greater than zero");
        assertThat(create(Map.of("amount", 100, "method", "BOLETO")).statusCode()).isEqualTo(400);
        assertThat(create(Map.of("amount", 100, "method", "CARD", "cardToken", "4111111111111111")).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/v1/payments/not-a-uuid", null, null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/v1/payments?size=1000", null, null).statusCode()).isEqualTo(400);
        assertThat(call("GET", "/v1/payments/" + UUID.randomUUID(), null, null).statusCode()).isEqualTo(404);
    }

    @Test
    void swaggerAndHealthAreAvailable() throws Exception {
        var docs = send("GET", "/v3/api-docs", null, Map.of());
        assertThat(docs.statusCode()).isEqualTo(200);
        assertThat(docs.body()).contains("/v1/payments/summary", "/v1/payments/{id}/refunds",
                "/v1/payments/{id}/pix-confirmation", "Idempotency-Key", "X-Merchant-Id", "X-Internal-Key");
        assertThat(send("GET", "/swagger-ui/index.html", null, Map.of()).statusCode()).isEqualTo(200);
        var health = send("GET", "/actuator/health", null, Map.of());
        assertThat(JSON.readTree(health.body()).get("status").asText()).isEqualTo("UP");
    }

    // ---- helpers -------------------------------------------------------------------------------------------

    private long paymentsOfMerchant() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM payment WHERE merchant_id = ?", Long.class, merchant.toString());
    }

    private static String key() {
        return "idem-" + UUID.randomUUID();
    }

    private HttpResponse<String> create(Map<String, Object> body) throws Exception {
        return call("POST", "/v1/payments", JSON.writeValueAsString(body), key());
    }

    private HttpResponse<String> call(String method, String path, String json, String idempotencyKey) throws Exception {
        return call(merchant, method, path, json, idempotencyKey);
    }

    private HttpResponse<String> call(UUID merchantId, String method, String path, String json, String idempotencyKey)
            throws Exception {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("X-Internal-Key", INTERNAL_KEY);
        headers.put("X-Merchant-Id", merchantId.toString());
        if (idempotencyKey != null) {
            headers.put("Idempotency-Key", idempotencyKey);
        }
        return send(method, path, json, headers);
    }

    private HttpResponse<String> send(String method, String path, String json, Map<String, String> headers)
            throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder(URI.create(
                    "http://localhost:" + environment.getRequiredProperty("local.server.port") + path));
            request.method(method, json == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(json));
            if (json != null) {
                request.header("Content-Type", "application/json");
            }
            headers.forEach(request::header);
            return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    @TestConfiguration
    static class RecordingPublisherConfiguration {

        @Bean
        RecordingEventPublisher recordingEventPublisher() {
            return new RecordingEventPublisher();
        }
    }

    static final class RecordingEventPublisher implements EventPublisher {

        private final List<OutboxMessage> published = Collections.synchronizedList(new ArrayList<>());
        private volatile boolean failNext;

        void failNext() {
            failNext = true;
        }

        List<OutboxMessage> published() {
            return List.copyOf(published);
        }

        @Override
        public void publish(List<OutboxMessage> messages) {
            if (failNext) {
                failNext = false;
                throw new IllegalStateException("broker down");
            }
            published.addAll(messages);
        }
    }
}
