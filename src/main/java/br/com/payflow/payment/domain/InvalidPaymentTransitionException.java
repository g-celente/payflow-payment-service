package br.com.payflow.payment.domain;

// The requested operation is not allowed in the payment's current status (409).
public final class InvalidPaymentTransitionException extends RuntimeException {

    public InvalidPaymentTransitionException(String message) {
        super(message);
    }
}
