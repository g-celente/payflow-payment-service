package br.com.payflow.payment.features.refundpayment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.Money;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentNotFoundException;

public final class RefundPaymentHandler {

    private final PaymentRefundStore payments;
    private final GatewayClient gateway;
    private final Clock clock;

    public RefundPaymentHandler(PaymentRefundStore payments, GatewayClient gateway, Clock clock) {
        this.payments = Objects.requireNonNull(payments);
        this.gateway = Objects.requireNonNull(gateway);
        this.clock = Objects.requireNonNull(clock);
    }

    public Payment execute(RefundPaymentCommand command) {
        Payment payment = payments.findById(command.merchantId(), command.paymentId())
                .orElseThrow(() -> new PaymentNotFoundException(command.paymentId()));
        // Validated before the gateway call, so an invalid refund never reaches it.
        Money amount = payment.refundAmountFor(command.amount());
        UUID refundId = UUID.randomUUID();
        gateway.refund(payment.gatewayReference(), refundId, amount);
        payment.refund(refundId, amount.cents(), command.reason(), Instant.now(clock));
        payments.save(payment);
        return payment;
    }
}
