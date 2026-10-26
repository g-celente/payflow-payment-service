package br.com.payflow.payment.features.listpayments;

import java.time.Instant;

import br.com.payflow.payment.domain.DomainValidationException;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;
import br.com.payflow.payment.support.InMemoryPaymentStore;
import org.junit.jupiter.api.Test;

import static br.com.payflow.payment.support.TestPayments.MERCHANT;
import static br.com.payflow.payment.support.TestPayments.OTHER_MERCHANT;
import static br.com.payflow.payment.support.TestPayments.authorizedCard;
import static br.com.payflow.payment.support.TestPayments.pendingPix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ListPaymentsHandlerTests {

    private final Payment card = authorizedCard(100);
    private final Payment pix = pendingPix(200);
    private final ListPaymentsHandler handler = new ListPaymentsHandler(new InMemoryPaymentStore().with(card, pix));

    @Test
    void filtersByMerchantStatusAndMethod() {
        assertThat(handler.execute(new ListPaymentsQuery(MERCHANT, null, null, null, null, 0, 20)).totalItems()).isEqualTo(2);
        assertThat(handler.execute(new ListPaymentsQuery(MERCHANT, PaymentStatus.PENDING, null, null, null, 0, 20)).items())
                .containsExactly(pix);
        assertThat(handler.execute(new ListPaymentsQuery(MERCHANT, null, PaymentMethod.CARD, null, null, 0, 20)).items())
                .containsExactly(card);
        assertThat(handler.execute(new ListPaymentsQuery(OTHER_MERCHANT, null, null, null, null, 0, 20)).items()).isEmpty();
    }

    @Test
    void queryIsValidated() {
        assertThatThrownBy(() -> new ListPaymentsQuery(MERCHANT, null, null, null, null, -1, 20)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new ListPaymentsQuery(MERCHANT, null, null, null, null, 0, 101)).isInstanceOf(DomainValidationException.class);
        Instant now = Instant.now();
        assertThatThrownBy(() -> new ListPaymentsQuery(MERCHANT, null, null, now, now, 0, 20)).isInstanceOf(DomainValidationException.class);
    }
}
