package br.com.payflow.payment.features.createpayment;

import br.com.payflow.payment.domain.Payment;

// Output port used only by the create slice.
public interface NewPaymentStore {

    // Stores the payment and its pending events in one transaction.
    void add(Payment payment);
}
