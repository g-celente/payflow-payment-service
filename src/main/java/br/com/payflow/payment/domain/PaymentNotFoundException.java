package br.com.payflow.payment.domain;

import java.util.UUID;

// Also used when the payment belongs to another merchant, so callers cannot probe other merchants' ids.
public final class PaymentNotFoundException extends RuntimeException {

    public PaymentNotFoundException(UUID id) {
        super("Payment " + id + " not found");
    }
}
