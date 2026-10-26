package br.com.payflow.payment.infrastructure.web;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// Reservations of Idempotency-Key per merchant (table idempotency_key).
interface IdempotencyStore {

    // Reserves the key. Empty = reserved for this request; otherwise the earlier request's record.
    Optional<StoredRequest> reserve(UUID merchantId, String key, String requestHash, Instant now, Instant expiresAt);

    void complete(UUID merchantId, String key, int status, String body);

    // Frees a reservation whose request failed without a definitive answer (5xx), so it can be retried.
    void release(UUID merchantId, String key);

    // responseStatus null = still being processed.
    record StoredRequest(String requestHash, Integer responseStatus, String responseBody) {
    }
}
