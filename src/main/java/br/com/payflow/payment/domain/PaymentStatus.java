package br.com.payflow.payment.domain;

// PIX: PENDING -> CONFIRMED | CANCELED. Card: AUTHORIZED | DECLINED.
// AUTHORIZED/CONFIRMED -> PARTIALLY_REFUNDED -> REFUNDED (a full refund goes straight to REFUNDED).
public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    DECLINED,
    CONFIRMED,
    CANCELED,
    PARTIALLY_REFUNDED,
    REFUNDED;

    public boolean isRefundable() {
        return this == AUTHORIZED || this == CONFIRMED || this == PARTIALLY_REFUNDED;
    }
}
