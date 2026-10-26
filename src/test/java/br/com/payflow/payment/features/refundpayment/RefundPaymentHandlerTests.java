package br.com.payflow.payment.features.refundpayment;

import java.util.UUID;

import br.com.payflow.payment.domain.DomainValidationException;
import br.com.payflow.payment.domain.InvalidPaymentTransitionException;
import br.com.payflow.payment.domain.Money;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentEventType;
import br.com.payflow.payment.domain.PaymentNotFoundException;
import br.com.payflow.payment.domain.PaymentStatus;
import br.com.payflow.payment.support.FakeGateway;
import br.com.payflow.payment.support.InMemoryPaymentStore;
import org.junit.jupiter.api.Test;

import static br.com.payflow.payment.support.TestPayments.CLOCK;
import static br.com.payflow.payment.support.TestPayments.MERCHANT;
import static br.com.payflow.payment.support.TestPayments.OTHER_MERCHANT;
import static br.com.payflow.payment.support.TestPayments.authorizedCard;
import static br.com.payflow.payment.support.TestPayments.pendingPix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefundPaymentHandlerTests {

    private final Payment card = authorizedCard(10000);
    private final Payment pix = pendingPix(5000);
    private final InMemoryPaymentStore store = new InMemoryPaymentStore().with(card, pix);
    private final FakeGateway gateway = new FakeGateway();
    private final RefundPaymentHandler handler = new RefundPaymentHandler(store, gateway, CLOCK);

    @Test
    void refundsThroughTheGatewayAndStoresTheEvent() {
        Payment refunded = handler.execute(new RefundPaymentCommand(MERCHANT, card.id(), 3000L, "avaria"));
        assertThat(refunded.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(gateway.refunds()).containsExactly(Money.of(3000, "BRL"));
        assertThat(store.outbox()).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(PaymentEventType.REFUNDED);
            assertThat(event.refund().reason()).isEqualTo("avaria");
        });
    }

    @Test
    void withoutAmountRefundsTheRest() {
        handler.execute(new RefundPaymentCommand(MERCHANT, card.id(), 3000L, null));
        Payment refunded = handler.execute(new RefundPaymentCommand(MERCHANT, card.id(), null, null));
        assertThat(refunded.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(gateway.refunds()).containsExactly(Money.of(3000, "BRL"), Money.of(7000, "BRL"));
    }

    @Test
    void invalidRefundsNeverReachTheGateway() {
        assertThatThrownBy(() -> handler.execute(new RefundPaymentCommand(MERCHANT, card.id(), 10001L, null)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> handler.execute(new RefundPaymentCommand(MERCHANT, pix.id(), null, null)))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> handler.execute(new RefundPaymentCommand(OTHER_MERCHANT, card.id(), null, null)))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> handler.execute(new RefundPaymentCommand(MERCHANT, UUID.randomUUID(), null, null)))
                .isInstanceOf(PaymentNotFoundException.class);
        assertThat(gateway.refunds()).isEmpty();
        assertThat(store.saves()).isZero();
    }
}
