package br.com.payflow.payment.infrastructure.web;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FilterTests {

    private static final String KEY = "internal-key-for-tests-0123456789abcdef";
    private static final String MERCHANT = "00000000-0000-0000-0000-000000000001";

    // ---- PlatformCallerFilter ------------------------------------------------------------------------------

    private final PlatformCallerFilter platform = new PlatformCallerFilter(KEY);

    private MockHttpServletResponse callPlatform(String path, String key, String merchant) throws Exception {
        var request = new MockHttpServletRequest("GET", path);
        if (key != null) {
            request.addHeader("X-Internal-Key", key);
        }
        if (merchant != null) {
            request.addHeader("X-Merchant-Id", merchant);
        }
        var response = new MockHttpServletResponse();
        platform.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void bffWithKeyAndMerchantPasses() throws Exception {
        assertThat(callPlatform("/v1/payments", KEY, MERCHANT).getStatus()).isEqualTo(200);
    }

    @Test
    void wrongOrMissingKeyIsUnauthorized() throws Exception {
        for (String key : new String[]{null, "", "wrong", KEY + "x"}) {
            var response = callPlatform("/v1/payments", key, MERCHANT);
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeader("WWW-Authenticate")).isEqualTo("ApiKey header=\"X-Internal-Key\"");
            assertThat(response.getContentType()).isEqualTo("application/problem+json");
        }
    }

    @Test
    void merchantIdMustBeAUuid() throws Exception {
        for (String merchant : new String[]{null, "", "loja-1", "1-1-1-1-1"}) {
            assertThat(callPlatform("/v1/payments", KEY, merchant).getStatus()).isEqualTo(400);
        }
    }

    @Test
    void healthAndSwaggerAreOpen() throws Exception {
        for (String path : new String[]{"/actuator/health", "/swagger-ui.html", "/v3/api-docs"}) {
            assertThat(callPlatform(path, null, null).getStatus()).as(path).isEqualTo(200);
        }
    }

    // ---- IdempotencyFilter ---------------------------------------------------------------------------------

    private final InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
    private final IdempotencyFilter idempotency = new IdempotencyFilter(store, Duration.ofHours(24),
            Clock.fixed(Instant.parse("2026-10-26T15:00:00Z"), ZoneOffset.UTC));

    private MockHttpServletResponse post(String path, String key, String body, FilterChain chain)
            throws IOException, ServletException {
        var request = new MockHttpServletRequest("POST", path);
        request.addHeader("X-Merchant-Id", MERCHANT);
        if (key != null) {
            request.addHeader("Idempotency-Key", key);
        }
        request.setContent(body.getBytes(StandardCharsets.UTF_8));
        var response = new MockHttpServletResponse();
        idempotency.doFilter(request, response, chain);
        return response;
    }

    private static FilterChain answering(int status, AtomicInteger calls) {
        return (request, response) -> {
            calls.incrementAndGet();
            assertThat(new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8)).startsWith("{");
            ((HttpServletResponse) response).setStatus(status);
            response.setContentType("application/json");
            response.getOutputStream().write(("{\"call\":" + calls.get() + "}").getBytes(StandardCharsets.UTF_8));
        };
    }

    @Test
    void repeatWithSameBodyReplaysTheStoredAnswer() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        var first = post("/v1/payments", "k1", "{\"amount\":1}", answering(201, calls));
        var second = post("/v1/payments", "k1", "{\"amount\":1}", answering(201, calls));
        assertThat(calls).hasValue(1);
        assertThat(first.getStatus()).isEqualTo(201);
        assertThat(first.getContentAsString()).isEqualTo("{\"call\":1}");
        assertThat(second.getStatus()).isEqualTo(201);
        assertThat(second.getContentAsString()).isEqualTo("{\"call\":1}");
        assertThat(second.getHeader("Idempotent-Replayed")).isEqualTo("true");
    }

    @Test
    void sameKeyWithAnotherBodyOrPathIs422() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        post("/v1/payments", "k2", "{\"amount\":1}", answering(201, calls));
        assertThat(post("/v1/payments", "k2", "{\"amount\":2}", answering(201, calls)).getStatus()).isEqualTo(422);
        assertThat(post("/v1/payments/123e4567-e89b-12d3-a456-426614174000/refunds", "k2", "{\"amount\":1}",
                answering(201, calls)).getStatus()).isEqualTo(422);
        assertThat(calls).hasValue(1);
    }

    @Test
    void repeatWhileTheFirstIsRunningIs409() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        FilterChain repeatInside = (request, response) -> {
            calls.incrementAndGet();
            var inner = post("/v1/payments", "k3", "{\"amount\":1}", answering(201, calls));
            assertThat(inner.getStatus()).isEqualTo(409);
            ((HttpServletResponse) response).setStatus(201);
        };
        assertThat(post("/v1/payments", "k3", "{\"amount\":1}", repeatInside).getStatus()).isEqualTo(201);
        assertThat(calls).hasValue(1);
    }

    @Test
    void serverErrorsAndExceptionsAreNotStored() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertThat(post("/v1/payments", "k4", "{\"amount\":1}", answering(504, calls)).getStatus()).isEqualTo(504);
        assertThat(post("/v1/payments", "k4", "{\"amount\":1}", answering(201, calls)).getStatus()).isEqualTo(201);
        assertThat(calls).hasValue(2);

        FilterChain exploding = (request, response) -> {
            throw new IllegalStateException("boom");
        };
        assertThatThrownBy(() -> post("/v1/payments", "k5", "{}", exploding)).hasMessage("boom");
        assertThat(post("/v1/payments", "k5", "{}", answering(201, calls)).getStatus()).isEqualTo(201);
    }

    @Test
    void clientErrorsAreStoredLikeSuccesses() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        post("/v1/payments", "k6", "{\"amount\":0}", answering(400, calls));
        var replay = post("/v1/payments", "k6", "{\"amount\":0}", answering(201, calls));
        assertThat(replay.getStatus()).isEqualTo(400);
        assertThat(replay.getContentType()).isEqualTo("application/problem+json");
        assertThat(calls).hasValue(1);
    }

    @Test
    void keyIsRequiredOnlyOnCreateAndRefund() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        assertThat(post("/v1/payments", null, "{}", answering(201, calls)).getStatus()).isEqualTo(400);
        assertThat(post("/v1/payments", "x".repeat(256), "{}", answering(201, calls)).getStatus()).isEqualTo(400);
        assertThat(post("/v1/payments/123e4567-e89b-12d3-a456-426614174000/pix-confirmation", null, "{}",
                answering(200, calls)).getStatus()).isEqualTo(200);
        assertThat(calls).hasValue(1);
    }

    private static final class InMemoryIdempotencyStore implements IdempotencyStore {

        private final Map<String, StoredRequest> records = new HashMap<>();

        @Override
        public Optional<StoredRequest> reserve(UUID merchantId, String key, String requestHash, Instant now,
                Instant expiresAt) {
            StoredRequest existing = records.putIfAbsent(merchantId + key, new StoredRequest(requestHash, null, null));
            return Optional.ofNullable(existing);
        }

        @Override
        public void complete(UUID merchantId, String key, int status, String body) {
            records.computeIfPresent(merchantId + key, (id, record) -> new StoredRequest(record.requestHash(), status, body));
        }

        @Override
        public void release(UUID merchantId, String key) {
            records.computeIfPresent(merchantId + key, (id, record) -> record.responseStatus() == null ? null : record);
        }
    }
}
