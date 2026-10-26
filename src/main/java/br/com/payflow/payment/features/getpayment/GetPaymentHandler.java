package br.com.payflow.payment.features.getpayment;

import java.util.Objects;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentNotFoundException;

public final class GetPaymentHandler {

    private final PaymentReader payments;

    public GetPaymentHandler(PaymentReader payments) {
        this.payments = Objects.requireNonNull(payments);
    }

    public Payment execute(UUID merchantId, UUID paymentId) {
        return payments.findById(merchantId, paymentId).orElseThrow(() -> new PaymentNotFoundException(paymentId));
    }
}
