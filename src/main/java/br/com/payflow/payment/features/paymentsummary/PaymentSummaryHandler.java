package br.com.payflow.payment.features.paymentsummary;

import java.util.Objects;

public final class PaymentSummaryHandler {

    private final PaymentStatistics statistics;

    public PaymentSummaryHandler(PaymentStatistics statistics) {
        this.statistics = Objects.requireNonNull(statistics);
    }

    public PaymentSummary execute(PaymentSummaryQuery query) {
        return statistics.summarize(query);
    }
}
