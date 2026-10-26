package br.com.payflow.payment.features.createpayment;

import java.util.Map;
import java.util.UUID;

import br.com.payflow.payment.domain.PaymentMethod;

// amount in cents; currency defaults to BRL; cardToken only for CARD.
public record CreatePaymentCommand(
        UUID merchantId,
        Long amount,
        String currency,
        PaymentMethod method,
        String cardToken,
        String description,
        Map<String, String> metadata) {
}
