package br.com.payflow.payment.features.cancelpayment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentNotFoundException;

// Cancels a PENDING payment. The record stays, with status CANCELED.
public final class CancelPaymentHandler {

    private final PaymentCancellationStore payments;
    private final Clock clock;

    public CancelPaymentHandler(PaymentCancellationStore payments, Clock clock) {
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }

    public Payment execute(UUID merchantId, UUID paymentId) {
        Payment payment = payments.findById(merchantId, paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        payment.cancel(Instant.now(clock));
        payments.save(payment);
        return payment;
    }
}
