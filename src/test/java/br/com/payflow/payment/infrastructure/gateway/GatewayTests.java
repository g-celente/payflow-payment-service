package br.com.payflow.payment.infrastructure.gateway;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.payflow.payment.domain.CardToken;
import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.GatewayTimeoutException;
import br.com.payflow.payment.domain.GatewayUnavailableException;
import br.com.payflow.payment.domain.Money;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GatewayTests {

    private static final Money AMOUNT = Money.of(1990, "BRL");
    private final SimulatedGatewayClient simulated = new SimulatedGatewayClient(Duration.ofMillis(500));

    @Test
    void tokenDecidesTheCardOutcome() {
        var approved = simulated.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_approved"));
        assertThat(approved.approved()).isTrue();
        assertThat(approved.gatewayReference()).startsWith("gw_ch_");
        assertThat(approved.cardLast4()).isEqualTo("4242");
        assertThat(simulated.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_declined")).approved()).isFalse();
        assertThat(simulated.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_visa_1234")).cardLast4())
                .isEqualTo("1234");
    }

    @Test
    void pixCodeFollowsTheBrCodeFormat() {
        UUID paymentId = UUID.fromString("123e4567-e89b-12d3-a456-426614174000");
        String code = simulated.createPixCharge(paymentId, Money.of(1990, "BRL")).qrCode();
        assertThat(code).startsWith("000201").contains("br.gov.bcb.pix").contains(paymentId.toString())
                .contains("540519.90").contains("5802BR").matches(".*6304[0-9A-F]{4}");
    }

    @Test
    void crcMatchesTheStandardCheckValue() {
        // CRC-16/CCITT-FALSE("123456789") = 29B1.
        assertThat(SimulatedGatewayClient.PixCopyPaste.crc16("123456789")).isEqualTo("29B1");
    }

    @Test
    void slowGatewayTimesOutQuickly() {
        try (var resilient = new ResilientGatewayClient(simulated, Duration.ofMillis(100), Duration.ofSeconds(30))) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> resilient.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_timeout")))
                    .isInstanceOf(GatewayTimeoutException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(450));
            assertThat(resilient.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_approved")).approved()).isTrue();
        }
    }

    @Test
    void repeatedFailuresOpenTheCircuitAndStopCallingTheGateway() {
        AtomicInteger calls = new AtomicInteger();
        GatewayClient failing = new GatewayClient() {
            @Override
            public CardAuthorization authorizeCard(UUID paymentId, Money amount, CardToken token) {
                calls.incrementAndGet();
                throw new IllegalStateException("gateway down");
            }

            @Override
            public PixCharge createPixCharge(UUID paymentId, Money amount) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void refund(String gatewayReference, UUID refundId, Money amount) {
                throw new UnsupportedOperationException();
            }
        };
        try (var resilient = new ResilientGatewayClient(failing, Duration.ofSeconds(1), Duration.ofSeconds(30))) {
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThatThrownBy(() -> resilient.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_approved")))
                        .isInstanceOf(IllegalStateException.class);
            }
            assertThat(resilient.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);
            assertThatThrownBy(() -> resilient.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_approved")))
                    .isInstanceOf(GatewayUnavailableException.class);
            assertThat(calls).hasValue(5);
        }
    }

    @Test
    void declinesAreAnswersNotFailures() {
        try (var resilient = new ResilientGatewayClient(simulated, Duration.ofSeconds(1), Duration.ofSeconds(30))) {
            for (int attempt = 0; attempt < 10; attempt++) {
                assertThat(resilient.authorizeCard(UUID.randomUUID(), AMOUNT, new CardToken("tok_declined")).approved()).isFalse();
            }
            assertThat(resilient.circuitState()).isEqualTo(CircuitBreaker.State.CLOSED);
        }
    }
}
