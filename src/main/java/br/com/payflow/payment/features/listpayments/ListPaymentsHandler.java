package br.com.payflow.payment.features.listpayments;

import java.util.Objects;

public final class ListPaymentsHandler {

    private final PaymentListing payments;

    public ListPaymentsHandler(PaymentListing payments) {
        this.payments = Objects.requireNonNull(payments);
    }

    public PaymentPage execute(ListPaymentsQuery query) {
        return payments.list(query);
    }
}
