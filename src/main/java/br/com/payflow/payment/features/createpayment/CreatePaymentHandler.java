package br.com.payflow.payment.features.createpayment;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import br.com.payflow.payment.domain.CardToken;
import br.com.payflow.payment.domain.DomainValidationException;
import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.Money;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentDetails;

public final class CreatePaymentHandler {

    private final NewPaymentStore payments;
    private final GatewayClient gateway;
    private final Clock clock;

    public CreatePaymentHandler(NewPaymentStore payments, GatewayClient gateway, Clock clock) {
        this.payments = Objects.requireNonNull(payments);
        this.gateway = Objects.requireNonNull(gateway);
        this.clock = Objects.requireNonNull(clock);
    }

    // The gateway is called before anything is stored and outside any transaction:
    // a timeout leaves no payment behind, so the same request can be retried.
    public Payment execute(CreatePaymentCommand command) {
        if (command.method() == null) {
            throw new DomainValidationException("method must be CARD or PIX");
        }
        Money amount = Money.positive(command.amount(), command.currency() == null ? Money.BRL : command.currency());
        PaymentDetails details = new PaymentDetails(command.description(), command.metadata());
        UUID id = UUID.randomUUID();
        Payment payment = switch (command.method()) {
            case CARD -> {
                CardToken token = new CardToken(command.cardToken());
                yield Payment.card(id, command.merchantId(), amount, token,
                        gateway.authorizeCard(id, amount, token), details, Instant.now(clock));
            }
            case PIX -> {
                if (command.cardToken() != null) {
                    throw new DomainValidationException("cardToken is only accepted for CARD payments");
                }
                yield Payment.pix(id, command.merchantId(), amount, gateway.createPixCharge(id, amount), details,
                        Instant.now(clock));
            }
        };
        payments.add(payment);
        return payment;
    }
}
