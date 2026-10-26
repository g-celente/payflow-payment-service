package br.com.payflow.payment.features.updatepayment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentNotFoundException;

public final class UpdatePaymentHandler {

    private final PaymentDetailsStore payments;
    private final Clock clock;

    public UpdatePaymentHandler(PaymentDetailsStore payments, Clock clock) {
        this.payments = Objects.requireNonNull(payments);
        this.clock = Objects.requireNonNull(clock);
    }

    public Payment execute(UpdatePaymentCommand command) {
        Payment payment = payments.findById(command.merchantId(), command.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId()));
        payment.changeDetails(payment.details().merge(command.description(), command.metadata()), Instant.now(clock));
        payments.save(payment);
        return payment;
    }
}
