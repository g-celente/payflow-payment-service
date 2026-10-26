package br.com.payflow.payment.domain;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static br.com.payflow.payment.support.TestPayments.MERCHANT;
import static br.com.payflow.payment.support.TestPayments.NOW;
import static br.com.payflow.payment.support.TestPayments.authorizedCard;
import static br.com.payflow.payment.support.TestPayments.declinedCard;
import static br.com.payflow.payment.support.TestPayments.pendingPix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentTests {

    private static final java.time.Instant LATER = NOW.plusSeconds(60);

    @Test
    void approvedCardIsBornAuthorizedWithItsEvent() {
        Payment payment = authorizedCard(1990);
        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.cardLast4()).isEqualTo("4242");
        List<PaymentEvent> events = payment.pullEvents();
        assertThat(events).singleElement().satisfies(event -> {
            assertThat(event.type()).isEqualTo(PaymentEventType.AUTHORIZED);
            assertThat(event.paymentId()).isEqualTo(payment.id());
            assertThat(event.merchantId()).isEqualTo(MERCHANT);
            assertThat(event.amount().cents()).isEqualTo(1990);
            assertThat(event.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        });
        assertThat(payment.pullEvents()).as("events are handed over once").isEmpty();
    }

    @Test
    void declinedCardIsBornDeclinedAndCannotMoveOn() {
        Payment payment = declinedCard(1990);
        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(payment.pullEvents()).extracting(PaymentEvent::type).containsExactly(PaymentEventType.DECLINED);
        assertThatThrownBy(() -> payment.refund(UUID.randomUUID(), null, null, LATER))
                .isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> payment.cancel(LATER)).isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> payment.confirmPix(LATER)).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void pixGoesFromPendingToConfirmed() {
        Payment payment = pendingPix(5000);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.pixQrCode()).isNotBlank();
        payment.confirmPix(LATER);
        assertThat(payment.status()).isEqualTo(PaymentStatus.CONFIRMED);
        assertThat(payment.updatedAt()).isEqualTo(LATER);
        assertThat(payment.pullEvents()).extracting(PaymentEvent::type)
                .containsExactly(PaymentEventType.PENDING, PaymentEventType.CONFIRMED);
        assertThatThrownBy(() -> payment.confirmPix(LATER)).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void onlyPendingPaymentsCanBeCanceled() {
        Payment pix = pendingPix(5000);
        pix.cancel(LATER);
        assertThat(pix.status()).isEqualTo(PaymentStatus.CANCELED);
        assertThat(pix.pullEvents()).extracting(PaymentEvent::type).endsWith(PaymentEventType.CANCELED);
        assertThatThrownBy(() -> pix.confirmPix(LATER)).isInstanceOf(InvalidPaymentTransitionException.class);
        assertThatThrownBy(() -> authorizedCard(100).cancel(LATER))
                .isInstanceOf(InvalidPaymentTransitionException.class).hasMessageContaining("refund it instead");
    }

    @Test
    void cardCannotBeConfirmedAsPix() {
        assertThatThrownBy(() -> authorizedCard(100).confirmPix(LATER)).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void partialThenRemainingRefund() {
        Payment payment = authorizedCard(10000);
        payment.pullEvents();

        Refund first = payment.refund(UUID.randomUUID(), 2500L, "item devolvido", LATER);
        assertThat(first.amount().cents()).isEqualTo(2500);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(payment.refundableAmount().cents()).isEqualTo(7500);

        Refund rest = payment.refund(UUID.randomUUID(), null, null, LATER);
        assertThat(rest.amount().cents()).isEqualTo(7500);
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(payment.refundedAmount().cents()).isEqualTo(10000);

        List<PaymentEvent> events = payment.pullEvents();
        assertThat(events).extracting(PaymentEvent::type).containsExactly(PaymentEventType.REFUNDED, PaymentEventType.REFUNDED);
        assertThat(events.get(0).status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(events.get(0).refund()).isEqualTo(first);
        assertThat(events.get(1).refundedCents()).isEqualTo(10000);
        assertThatThrownBy(() -> payment.refund(UUID.randomUUID(), null, null, LATER))
                .isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void fullRefundGoesStraightToRefunded() {
        Payment payment = authorizedCard(10000);
        payment.refund(UUID.randomUUID(), null, null, LATER);
        assertThat(payment.status()).isEqualTo(PaymentStatus.REFUNDED);
    }

    @Test
    void refundCannotExceedTheBalanceNorBeEmpty() {
        Payment payment = authorizedCard(10000);
        payment.refund(UUID.randomUUID(), 6000L, null, LATER);
        assertThatThrownBy(() -> payment.refundAmountFor(4001L))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("4000");
        assertThatThrownBy(() -> payment.refundAmountFor(0L)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> payment.refund(UUID.randomUUID(), 100L, "x".repeat(256), LATER))
                .isInstanceOf(DomainValidationException.class);
        assertThat(payment.refunds()).hasSize(1);
        assertThatThrownBy(() -> pendingPix(100).refundAmountFor(null)).isInstanceOf(InvalidPaymentTransitionException.class);
    }

    @Test
    void confirmedPixCanBeRefunded() {
        Payment payment = pendingPix(5000);
        payment.confirmPix(LATER);
        payment.refund(UUID.randomUUID(), 1000L, null, LATER);
        assertThat(payment.status()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void detailsChangeWithoutEvents() {
        Payment payment = authorizedCard(100);
        payment.pullEvents();
        payment.changeDetails(new PaymentDetails("Pedido 42", java.util.Map.of("order_id", "42")), LATER);
        assertThat(payment.details().description()).isEqualTo("Pedido 42");
        assertThat(payment.updatedAt()).isEqualTo(LATER);
        assertThat(payment.pullEvents()).isEmpty();
    }

    @Test
    void restoredPaymentHasNoEvents() {
        Payment original = authorizedCard(100);
        Payment restored = Payment.restore(original.id(), MERCHANT, original.amount(), original.method(), original.status(),
                original.cardToken(), original.cardLast4(), null, original.gatewayReference(), PaymentDetails.EMPTY,
                List.of(), NOW, NOW, 3);
        assertThat(restored.pullEvents()).isEmpty();
        assertThat(restored.version()).isEqualTo(3);
        assertThatThrownBy(() -> Payment.restore(original.id(), MERCHANT, Money.of(0, "BRL"), PaymentMethod.CARD,
                PaymentStatus.AUTHORIZED, null, null, null, null, PaymentDetails.EMPTY, List.of(), NOW, NOW, 0))
                .isInstanceOf(DomainValidationException.class);
    }
}
