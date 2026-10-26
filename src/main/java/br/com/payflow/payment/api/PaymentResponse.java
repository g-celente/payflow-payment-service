package br.com.payflow.payment.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;

// Public view of a payment shared by the endpoints. Amounts in cents; the card token is never returned.
public record PaymentResponse(
        UUID id,
        UUID merchantId,
        long amount,
        String currency,
        PaymentMethod method,
        PaymentStatus status,
        long refundedAmount,
        Card card,
        Pix pix,
        String gatewayReference,
        String description,
        Map<String, String> metadata,
        List<RefundView> refunds,
        Instant createdAt,
        Instant updatedAt) {

    public record Card(String last4) {
    }

    public record Pix(String qrCode) {
    }

    public record RefundView(UUID id, long amount, String reason, Instant createdAt) {
    }

    public static PaymentResponse from(Payment payment) {
        Card card = payment.method() == PaymentMethod.CARD ? new Card(payment.cardLast4()) : null;
        Pix pix = payment.method() == PaymentMethod.PIX ? new Pix(payment.pixQrCode()) : null;
        List<RefundView> refunds = payment.refunds().stream()
                .map(refund -> new RefundView(refund.id(), refund.amount().cents(), refund.reason(), refund.createdAt()))
                .toList();
        return new PaymentResponse(payment.id(), payment.merchantId(), payment.amount().cents(),
                payment.amount().currency(), payment.method(), payment.status(), payment.refundedAmount().cents(),
                card, pix, payment.gatewayReference(), payment.details().description(), payment.details().metadata(),
                refunds, payment.createdAt(), payment.updatedAt());
    }
}
