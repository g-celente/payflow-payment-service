package br.com.payflow.payment.features.paymentsummary;

import java.time.Instant;
import java.util.UUID;

import br.com.payflow.payment.domain.DomainValidationException;

// Optional period on createdAt: from inclusive, to exclusive.
public record PaymentSummaryQuery(UUID merchantId, Instant from, Instant to) {

    public PaymentSummaryQuery {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new DomainValidationException("from must be before to");
        }
    }
}
