package br.com.payflow.payment.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record Refund(UUID id, Money amount, String reason, Instant createdAt) {

    public static final int MAX_REASON_LENGTH = 255;

    public Refund {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(createdAt, "createdAt");
        if (amount.isZero()) {
            throw new DomainValidationException("Refund amount must be greater than zero cents");
        }
        if (reason != null && reason.length() > MAX_REASON_LENGTH) {
            throw new DomainValidationException("Refund reason must have at most " + MAX_REASON_LENGTH + " characters");
        }
    }
}
