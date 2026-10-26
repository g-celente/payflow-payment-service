package br.com.payflow.payment.features.listpayments;

import java.util.List;

import br.com.payflow.payment.domain.Payment;

public record PaymentPage(List<Payment> items, int page, int size, long totalItems) {

    public PaymentPage {
        items = List.copyOf(items);
    }

    public long totalPages() {
        return (totalItems + size - 1) / size;
    }
}
