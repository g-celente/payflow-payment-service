package br.com.payflow.payment.features.createpayment;

import java.util.Map;

import br.com.payflow.payment.domain.DomainValidationException;
import br.com.payflow.payment.domain.GatewayTimeoutException;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentEventType;
import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;
import br.com.payflow.payment.support.FakeGateway;
import br.com.payflow.payment.support.InMemoryPaymentStore;
import org.junit.jupiter.api.Test;

import static br.com.payflow.payment.support.TestPayments.CLOCK;
import static br.com.payflow.payment.support.TestPayments.MERCHANT;
import static br.com.payflow.payment.support.TestPayments.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreatePaymentHandlerTests {

    private final InMemoryPaymentStore store = new InMemoryPaymentStore();
    private final CreatePaymentHandler handler = new CreatePaymentHandler(store, new FakeGateway(), CLOCK);

    private static CreatePaymentCommand card(String token) {
        return new CreatePaymentCommand(MERCHANT, 1990L, null, PaymentMethod.CARD, token, "Pedido 1", Map.of("order_id", "1"));
    }

    @Test
    void approvedCardIsStoredAuthorizedWithItsEvent() {
        Payment payment = handler.execute(card("tok_approved"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.AUTHORIZED);
        assertThat(payment.amount().currency()).isEqualTo("BRL");
        assertThat(payment.merchantId()).isEqualTo(MERCHANT);
        assertThat(payment.createdAt()).isEqualTo(NOW);
        assertThat(payment.details().metadata()).containsEntry("order_id", "1");
        assertThat(store.findById(MERCHANT, payment.id())).contains(payment);
        assertThat(store.outbox()).extracting(event -> event.type()).containsExactly(PaymentEventType.AUTHORIZED);
    }

    @Test
    void declinedCardIsStillRecorded() {
        Payment payment = handler.execute(card("tok_declined"));
        assertThat(payment.status()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(store.outbox()).extracting(event -> event.type()).containsExactly(PaymentEventType.DECLINED);
    }

    @Test
    void pixIsStoredPendingWithQrCode() {
        Payment payment = handler.execute(new CreatePaymentCommand(MERCHANT, 5000L, "BRL", PaymentMethod.PIX, null, null, null));
        assertThat(payment.status()).isEqualTo(PaymentStatus.PENDING);
        assertThat(payment.pixQrCode()).isNotBlank();
        assertThat(store.outbox()).extracting(event -> event.type()).containsExactly(PaymentEventType.PENDING);
    }

    @Test
    void gatewayTimeoutStoresNothing() {
        assertThatThrownBy(() -> handler.execute(card("tok_timeout"))).isInstanceOf(GatewayTimeoutException.class);
        assertThat(store.size()).isZero();
        assertThat(store.outbox()).isEmpty();
    }

    @Test
    void invalidRequestsNeverReachTheGatewayOrTheStore() {
        assertThatThrownBy(() -> handler.execute(new CreatePaymentCommand(MERCHANT, 100L, null, null, null, null, null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("method");
        assertThatThrownBy(() -> handler.execute(new CreatePaymentCommand(MERCHANT, 0L, null, PaymentMethod.CARD, "tok_approved", null, null)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> handler.execute(new CreatePaymentCommand(MERCHANT, 100L, "USD", PaymentMethod.PIX, null, null, null)))
                .isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> handler.execute(new CreatePaymentCommand(MERCHANT, 100L, null, PaymentMethod.CARD, "4111111111111111", null, null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("cardToken");
        assertThatThrownBy(() -> handler.execute(new CreatePaymentCommand(MERCHANT, 100L, null, PaymentMethod.PIX, "tok_approved", null, null)))
                .isInstanceOf(DomainValidationException.class).hasMessageContaining("only accepted for CARD");
        assertThat(store.size()).isZero();
    }
}
