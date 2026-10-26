package br.com.payflow.payment.features.listpayments;

import java.time.Instant;
import java.util.UUID;

import br.com.payflow.payment.domain.DomainValidationException;
import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;

// Filters are optional; from is inclusive and to is exclusive, on createdAt. page starts at 0.
public record ListPaymentsQuery(UUID merchantId, PaymentStatus status, PaymentMethod method, Instant from, Instant to,
        int page, int size) {

    public static final int MAX_SIZE = 100;

    public ListPaymentsQuery {
        if (page < 0) {
            throw new DomainValidationException("page must be zero or greater");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new DomainValidationException("size must be between 1 and " + MAX_SIZE);
        }
        if (from != null && to != null && !from.isBefore(to)) {
            throw new DomainValidationException("from must be before to");
        }
    }
}
