package br.com.payflow.payment.domain;

// Published on the payment-events queue as CloudEvents types (see docs/asyncapi.yaml).
public enum PaymentEventType {
    AUTHORIZED("payment.authorized"),
    DECLINED("payment.declined"),
    PENDING("payment.pending"),
    CONFIRMED("payment.confirmed"),
    REFUNDED("payment.refunded"),
    CANCELED("payment.canceled");

    private final String wireName;

    PaymentEventType(String wireName) {
        this.wireName = wireName;
    }

    public String wireName() {
        return wireName;
    }
}
