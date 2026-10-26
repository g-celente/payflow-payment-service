package br.com.payflow.payment.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import br.com.payflow.payment.domain.CardToken;
import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.Money;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentDetails;

public final class TestPayments {

    public static final Instant NOW = Instant.parse("2026-10-26T15:00:00Z");
    public static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    public static final UUID MERCHANT = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID OTHER_MERCHANT = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private TestPayments() {
    }

    public static Payment authorizedCard(long cents) {
        return Payment.card(UUID.randomUUID(), MERCHANT, Money.of(cents, Money.BRL), new CardToken("tok_approved"),
                new GatewayClient.CardAuthorization(true, "gw_ch_1", "4242"), PaymentDetails.EMPTY, NOW);
    }

    public static Payment declinedCard(long cents) {
        return Payment.card(UUID.randomUUID(), MERCHANT, Money.of(cents, Money.BRL), new CardToken("tok_declined"),
                new GatewayClient.CardAuthorization(false, "gw_ch_2", "4242"), PaymentDetails.EMPTY, NOW);
    }

    public static Payment pendingPix(long cents) {
        return Payment.pix(UUID.randomUUID(), MERCHANT, Money.of(cents, Money.BRL),
                new GatewayClient.PixCharge("gw_pix_1", "000201...6304ABCD"), PaymentDetails.EMPTY, NOW);
    }

    // The same payment as stored and read back: same state, no pending events.
    public static Payment stored(Payment payment) {
        payment.pullEvents();
        return payment;
    }
}
