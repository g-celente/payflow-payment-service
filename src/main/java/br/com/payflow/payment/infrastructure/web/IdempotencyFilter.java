package br.com.payflow.payment.infrastructure.web;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import br.com.payflow.payment.api.ApiSecurity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

// Idempotency-Key on POST /v1/payments and POST /v1/payments/{id}/refunds (IETF draft
// "The Idempotency-Key HTTP Header Field"): the first request is processed and its answer stored for the TTL;
// a repeat with the same body gets the stored answer (Idempotent-Replayed: true); the same key with another
// body is 422; a repeat while the first is still running is 409. 5xx answers are not stored, so retries run again.
public final class IdempotencyFilter extends OncePerRequestFilter {

    static final String REPLAYED_HEADER = "Idempotent-Replayed";
    static final int MAX_KEY_LENGTH = 255;
    static final int MAX_BODY_BYTES = 64 * 1024;
    private static final Pattern PROTECTED = Pattern.compile("/v1/payments(/[0-9a-fA-F-]{36}/refunds)?");

    private final IdempotencyStore store;
    private final Duration ttl;
    private final Clock clock;

    IdempotencyFilter(IdempotencyStore store, Duration ttl, Clock clock) {
        this.store = store;
        this.ttl = ttl;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !PROTECTED.matcher(path).matches();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(ApiSecurity.IDEMPOTENCY_KEY_HEADER);
        if (key == null || key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            ProblemResponses.write(response, 400, "Bad Request", "Idempotency-Key header is required (1 to 255 characters)");
            return;
        }
        byte[] body = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
        if (body.length > MAX_BODY_BYTES) {
            ProblemResponses.write(response, 413, "Content Too Large", "Request body is limited to 64 KB");
            return;
        }
        UUID merchantId = UUID.fromString(request.getHeader(ApiSecurity.MERCHANT_ID_HEADER));
        String hash = hash(request, body);
        Instant now = clock.instant();

        var earlier = store.reserve(merchantId, key, hash, now, now.plus(ttl));
        if (earlier.isPresent()) {
            answerFromEarlier(earlier.get(), hash, response);
            return;
        }

        var cachedResponse = new ContentCachingResponseWrapper(response);
        try {
            chain.doFilter(new CachedBodyRequest(request, body), cachedResponse);
        } catch (IOException | ServletException | RuntimeException failure) {
            store.release(merchantId, key);
            throw failure;
        }
        int status = cachedResponse.getStatus();
        if (status >= 500) {
            store.release(merchantId, key);
        } else {
            store.complete(merchantId, key, status,
                    new String(cachedResponse.getContentAsByteArray(), StandardCharsets.UTF_8));
        }
        cachedResponse.copyBodyToResponse();
    }

    private static void answerFromEarlier(IdempotencyStore.StoredRequest earlier, String hash,
            HttpServletResponse response) throws IOException {
        if (!earlier.requestHash().equals(hash)) {
            ProblemResponses.write(response, 422, "Unprocessable Content",
                    "Idempotency-Key was already used with a different request");
            return;
        }
        if (earlier.responseStatus() == null) {
            ProblemResponses.write(response, 409, "Conflict", "A request with this Idempotency-Key is still being processed");
            return;
        }
        response.setStatus(earlier.responseStatus());
        response.setHeader(REPLAYED_HEADER, "true");
        response.setContentType(earlier.responseStatus() >= 400
                ? MediaType.APPLICATION_PROBLEM_JSON_VALUE
                : MediaType.APPLICATION_JSON_VALUE);
        if (earlier.responseBody() != null) {
            response.getOutputStream().write(earlier.responseBody().getBytes(StandardCharsets.UTF_8));
        }
    }

    // Same key + different method, path or body = a different request.
    private static String hash(HttpServletRequest request, byte[] body) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update((request.getMethod() + " " + request.getRequestURI() + "\n").getBytes(StandardCharsets.UTF_8));
            digest.update(body);
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    // The body was read to hash it; the controller reads it again from memory.
    private static final class CachedBodyRequest extends HttpServletRequestWrapper {

        private final byte[] body;

        CachedBodyRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override
                public int read() {
                    return source.read();
                }

                @Override
                public int read(byte[] buffer, int offset, int length) {
                    return source.read(buffer, offset, length);
                }

                @Override
                public boolean isFinished() {
                    return source.available() == 0;
                }

                @Override
                public boolean isReady() {
                    return true;
                }

                @Override
                public void setReadListener(ReadListener listener) {
                    throw new UnsupportedOperationException("Asynchronous reads are not supported");
                }
            };
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
        }

        @Override
        public int getContentLength() {
            return body.length;
        }

        @Override
        public long getContentLengthLong() {
            return body.length;
        }
    }
}
