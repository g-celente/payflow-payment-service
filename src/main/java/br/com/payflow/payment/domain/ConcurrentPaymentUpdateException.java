package br.com.payflow.payment.domain;

import java.util.UUID;

// Another request changed the payment first (optimistic lock); the caller may read it again and retry.
public final class ConcurrentPaymentUpdateException extends RuntimeException {

    public ConcurrentPaymentUpdateException(UUID id) {
        super("Payment " + id + " was changed by another request; read it again and retry");
    }
}
