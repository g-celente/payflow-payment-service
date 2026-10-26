package br.com.payflow.payment.domain;

// Raised when input breaks a domain invariant; the API answers 400 with this message.
public final class DomainValidationException extends IllegalArgumentException {

    public DomainValidationException(String message) {
        super(message);
    }
}
