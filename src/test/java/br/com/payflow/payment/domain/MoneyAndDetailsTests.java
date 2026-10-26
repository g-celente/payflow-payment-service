package br.com.payflow.payment.domain;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyAndDetailsTests {

    @Test
    void moneyIsBrlCentsWithinLimits() {
        assertThat(Money.positive(1990L, "BRL").cents()).isEqualTo(1990);
        assertThat(Money.of(0, "BRL").isZero()).isTrue();
        assertThatThrownBy(() -> Money.positive(1990L, "USD")).isInstanceOf(DomainValidationException.class).hasMessageContaining("BRL");
        assertThatThrownBy(() -> Money.positive(1990L, null)).isInstanceOf(DomainValidationException.class);
        for (Long cents : new Long[]{null, 0L, -1L}) {
            assertThatThrownBy(() -> Money.positive(cents, "BRL")).isInstanceOf(DomainValidationException.class);
        }
        assertThatThrownBy(() -> Money.positive(Money.MAX_CENTS + 1, "BRL")).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void moneyArithmetic() {
        Money ten = Money.of(1000, "BRL");
        assertThat(ten.minus(Money.of(250, "BRL"))).isEqualTo(Money.of(750, "BRL"));
        assertThat(ten.isGreaterThan(Money.of(999, "BRL"))).isTrue();
        assertThat(ten.isGreaterThan(ten)).isFalse();
    }

    @Test
    void cardTokenMustLookLikeAGatewayToken() {
        assertThat(new CardToken("tok_visa_1234").value()).isEqualTo("tok_visa_1234");
        for (String token : new String[]{null, "", "tok_", "4111111111111111", "tok-approved", "tok_" + "x".repeat(61)}) {
            assertThatThrownBy(() -> new CardToken(token)).isInstanceOf(DomainValidationException.class);
        }
    }

    @Test
    void detailsAreBoundedAndSorted() {
        var details = new PaymentDetails("  ", Map.of("order_id", "123", "a.key", "x"));
        assertThat(details.description()).isNull();
        assertThat(details.metadata().keySet()).containsExactly("a.key", "order_id");

        assertThatThrownBy(() -> new PaymentDetails("x".repeat(256), null)).isInstanceOf(DomainValidationException.class);
        Map<String, String> tooMany = new HashMap<>();
        for (int index = 0; index <= PaymentDetails.MAX_METADATA_ENTRIES; index++) {
            tooMany.put("k" + index, "v");
        }
        assertThatThrownBy(() -> new PaymentDetails(null, tooMany)).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new PaymentDetails(null, Map.of("bad key", "v"))).isInstanceOf(DomainValidationException.class);
        assertThatThrownBy(() -> new PaymentDetails(null, Map.of("k", "v".repeat(501)))).isInstanceOf(DomainValidationException.class);
        Map<String, String> nullValue = new HashMap<>();
        nullValue.put("k", null);
        assertThatThrownBy(() -> new PaymentDetails(null, nullValue)).isInstanceOf(DomainValidationException.class);
    }

    @Test
    void mergeKeepsWhatIsNotSent() {
        var details = new PaymentDetails("Pedido 1", Map.of("order_id", "1"));
        assertThat(details.merge(null, null)).isEqualTo(details);
        assertThat(details.merge("Pedido 2", null)).isEqualTo(new PaymentDetails("Pedido 2", Map.of("order_id", "1")));
        assertThat(details.merge("", Map.of())).isEqualTo(PaymentDetails.EMPTY);
    }
}
