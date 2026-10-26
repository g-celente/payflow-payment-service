package br.com.payflow.payment.features;

import java.util.Map;
import java.util.UUID;

import br.com.payflow.payment.domain.InvalidPaymentTransitionException;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentEventType;
import br.com.payflow.payment.domain.PaymentNotFoundException;
import br.com.payflow.payment.domain.PaymentStatus;
import br.com.payflow.payment.features.cancelpayment.CancelPaymentHandler;
import br.com.payflow.payment.features.confirmpixpayment.ConfirmPixPaymentHandler;
import br.com.payflow.payment.features.getpayment.GetPaymentHandler;
import br.com.payflow.payment.features.updatepayment.UpdatePaymentCommand;
import br.com.payflow.payment.features.updatepayment.UpdatePaymentHandler;
import br.com.payflow.payment.support.InMemoryPaymentStore;
import org.junit.jupiter.api.Test;

import static br.com.payflow.payment.support.TestPayments.CLOCK;
import static br.com.payflow.payment.support.TestPayments.MERCHANT;
import static br.com.payflow.payment.support.TestPayments.NOW;
import static br.com.payflow.payment.support.TestPayments.OTHER_MERCHANT;
import static br.com.payflow.payment.support.TestPayments.authorizedCard;
import static br.com.payflow.payment.support.TestPayments.pendingPix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Get, update, cancel and PIX confirmation: small slices, one test class.
class StatusChangeHandlersTests {

    private final Payment card = authorizedCard(10000);
    private final Payment pix = pendingPix(5000);
    private final InMemoryPaymentStore store = new InMemoryPaymentStore().with(card, pix);

    @Test
    void getReturnsOnlyTheMerchantsOwnPayment() {
        var handler = new GetPaymentHandler(store);
        assertThat(handler.execute(MERCHANT, card.id())).isSameAs(card);
        assertThatThrownBy(() -> handler.execute(OTHER_MERCHANT, card.id())).isInstanceOf(PaymentNotFoundException.class);
        assertThatThrownBy(() -> handler.execute(MERCHANT, UUID.randomUUID())).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void updateChangesOnlyWhatIsSent() {
        var handler = new UpdatePaymentHandler(store, CLOCK);
        handler.execute(new UpdatePaymentCommand(MERCHANT, card.id(), "Pedido 42", Map.of("order_id", "42")));
        Payment updated = handler.execute(new UpdatePaymentCommand(MERCHANT, card.id(), null, Map.of("order_id", "43")));
        assertThat(updated.details().description()).isEqualTo("Pedido 42");
        assertThat(updated.details().metadata()).containsExactlyEntriesOf(Map.of("order_id", "43"));
        assertThat(updated.updatedAt()).isEqualTo(NOW);
        assertThat(store.outbox()).isEmpty();
    }

    @Test
    void cancelOnlyPendingPayments() {
        var handler = new CancelPaymentHandler(store, CLOCK);
        assertThat(handler.execute(MERCHANT, pix.id()).status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(store.outbox()).extracting(event -> event.type()).containsExactly(PaymentEventType.CANCELED);
        assertThatThrownBy(() -> handler.execute(MERCHANT, card.id())).isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> handler.execute(OTHER_MERCHANT, pix.id())).isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    void confirmOnlyPendingPix() {
        var handler = new ConfirmPixPaymentHandler(store, CLOCK);
        assertThat(handler.execute(MERCHANT, pix.id()).status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(store.outbox()).extracting(event -> event.type()).containsExactly(PaymentEventType.CONFIRMED);
        assertThatThrownBy(() -> handler.execute(MERCHANT, pix.id())).isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> handler.execute(MERCHANT, card.id())).isInstanceOf(InvalidPaymentTransitionException.class);
    }
}
