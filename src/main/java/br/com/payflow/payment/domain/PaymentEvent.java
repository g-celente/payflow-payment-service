package br.com.payflow.payment.domain;

import java.time.Instant;
import java.util.UUID;

// Snapshot of the payment right after a status change. refund is set only on payment.refunded.
public record PaymentEvent(
        UUID id,
        PaymentEventType type,
        Instant occurredAt,
        UUID paymentId,
        UUID merchantId,
        PaymentMethod method,
        PaymentStatus status,
        Money amount,
        long refundedCents,
        Refund refund) {
}
