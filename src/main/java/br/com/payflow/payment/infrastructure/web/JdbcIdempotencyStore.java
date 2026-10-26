package br.com.payflow.payment.infrastructure.web;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

// The primary key (merchant_id, idem_key) makes the reservation atomic: of two concurrent requests with the
// same key, only one INSERT succeeds.
final class JdbcIdempotencyStore implements IdempotencyStore {

    private static final String INSERT = """
            INSERT INTO idempotency_key (merchant_id, idem_key, request_hash, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?)""";
    private static final String FIND = """
            SELECT request_hash, response_status, response_body, expires_at
            FROM idempotency_key WHERE merchant_id = ? AND idem_key = ?""";
    private static final String DELETE_EXPIRED =
            "DELETE FROM idempotency_key WHERE merchant_id = ? AND idem_key = ? AND expires_at < ?";
    private static final String COMPLETE =
            "UPDATE idempotency_key SET response_status = ?, response_body = ? WHERE merchant_id = ? AND idem_key = ?";
    private static final String RELEASE =
            "DELETE FROM idempotency_key WHERE merchant_id = ? AND idem_key = ? AND response_status IS NULL";

    private final JdbcTemplate jdbc;

    JdbcIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<StoredRequest> reserve(UUID merchantId, String key, String requestHash, Instant now,
            Instant expiresAt) {
        // Second attempt only after an expired or released record was removed.
        for (int attempt = 0; attempt < 2; attempt++) {
            try {
                jdbc.update(INSERT, merchantId.toString(), key, requestHash, utc(now), utc(expiresAt));
                return Optional.empty();
            } catch (DuplicateKeyException taken) {
                var existing = jdbc.query(FIND, (row, index) -> new Stored(
                        new StoredRequest(row.getString("request_hash"), (Integer) row.getObject("response_status"),
                                row.getString("response_body")),
                        row.getObject("expires_at", OffsetDateTime.class).toInstant()), merchantId.toString(), key);
                if (existing.isEmpty()) {
                    continue;
                }
                if (!existing.getFirst().expiresAt().isAfter(now)) {
                    jdbc.update(DELETE_EXPIRED, merchantId.toString(), key, utc(now.plusNanos(1)));
                    continue;
                }
                return Optional.of(existing.getFirst().request());
            }
        }
        throw new IllegalStateException("Could not reserve Idempotency-Key " + key);
    }

    @Override
    public void complete(UUID merchantId, String key, int status, String body) {
        jdbc.update(COMPLETE, status, body, merchantId.toString(), key);
    }

    @Override
    public void release(UUID merchantId, String key) {
        jdbc.update(RELEASE, merchantId.toString(), key);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private record Stored(StoredRequest request, Instant expiresAt) {
    }
}
