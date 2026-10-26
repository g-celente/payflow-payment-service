package br.com.payflow.payment.features.refundpayment;

import java.util.UUID;

// amount in cents; null refunds everything that is still refundable.
public record RefundPaymentCommand(UUID merchantId, UUID paymentId, Long amount, String reason) {
}
