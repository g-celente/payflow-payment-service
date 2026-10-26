package br.com.payflow.payment.domain;

// The circuit breaker is open after repeated gateway failures; calls are refused without trying (503).
public final class GatewayUnavailableException extends RuntimeException {

    public GatewayUnavailableException() {
        super("The payment gateway is unavailable; nothing was recorded, retry later");
    }
}
