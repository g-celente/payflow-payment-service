package br.com.payflow.payment.features.updatepayment;

import java.util.Map;
import java.util.UUID;

// PATCH: null keeps the current value; "" clears the description and {} clears the metadata.
public record UpdatePaymentCommand(UUID merchantId, UUID paymentId, String description, Map<String, String> metadata) {
}
