package br.com.payflow.payment.features.confirmpixpayment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentNotFoundException;

// Simulates the gateway telling us the buyer paid the QR Code.
public final class ConfirmPixPaymentHandler {

    private final PixConfirmationStore payments;
    private final Clock clock;

    public ConfirmPixPaymentHandler(PixConfirmationStore payments, Clock clock) {
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }

    public Payment execute(UUID merchantId, UUID paymentId) {
        Payment payment = payments.findById(merchantId, paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(paymentId));
        payment.confirmPix(Instant.now(clock));
        payments.save(payment);
        return payment;
    }
}
