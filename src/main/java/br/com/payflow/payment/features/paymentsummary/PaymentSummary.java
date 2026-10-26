package br.com.payflow.payment.features.paymentsummary;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;

// count and byStatus cover every payment. totalAmount and byMethod are the approved volume net of refunds
// (AUTHORIZED, CONFIRMED, PARTIALLY_REFUNDED and REFUNDED minus what was refunded), in cents:
// the base the calculate-fees Function uses. Every status and method is present, with zero when unused.
public record PaymentSummary(String currency, long totalAmount, long count,
        Map<PaymentStatus, Long> byStatus, Map<PaymentMethod, Long> byMethod) {

    public PaymentSummary {
        byStatus = complete(byStatus, PaymentStatus.class);
        byMethod = complete(byMethod, PaymentMethod.class);
    }

    private static <K extends Enum<K>> Map<K, Long> complete(Map<K, Long> values, Class<K> type) {
        EnumMap<K, Long> all = new EnumMap<>(type);
        for (K key : type.getEnumConstants()) {
            all.put(key, values.getOrDefault(key, 0L));
        }
        return Collections.unmodifiableMap(all);
    }
}
