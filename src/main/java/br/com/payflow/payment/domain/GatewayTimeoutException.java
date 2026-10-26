package br.com.payflow.payment.domain;

// The gateway did not answer in time. Nothing was recorded, so the same request can be retried (504).
public final class GatewayTimeoutException extends RuntimeException {

    public GatewayTimeoutException() {
        super("The payment gateway did not answer in time; nothing was recorded, retry with the same Idempotency-Key");
    }
}
