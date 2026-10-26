package br.com.payflow.payment.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

// Aggregate root. Every status change records a PaymentEvent; the store writes the payment and its events
// in one transaction (transactional outbox), and pullEvents() hands them over exactly once.
public final class Payment {

    private final UUID id;
    private final UUID merchantId;
    private final Money amount;
    private final PaymentMethod method;
    private PaymentStatus status;
    private final String cardToken;
    private final String cardLast4;
    private final String pixQrCode;
    private final String gatewayReference;
    private PaymentDetails details;
    private final List<Refund> refunds;
    private final Instant createdAt;
    private Instant updatedAt;
    private final long version;
    private final List<PaymentEvent> pendingEvents = new ArrayList<>();

    private Payment(UUID id, UUID merchantId, Money amount, PaymentMethod method, PaymentStatus status,
            String cardToken, String cardLast4, String pixQrCode, String gatewayReference, PaymentDetails details,
            List<Refund> refunds, Instant createdAt, Instant updatedAt, long version) {
        this.id = Objects.requireNonNull(id, "id");
        this.merchantId = Objects.requireNonNull(merchantId, "merchantId");
        this.amount = Objects.requireNonNull(amount, "amount");
        this.method = Objects.requireNonNull(method, "method");
        this.status = Objects.requireNonNull(status, "status");
        this.cardToken = cardToken;
        this.cardLast4 = cardLast4;
        this.pixQrCode = pixQrCode;
        this.gatewayReference = gatewayReference;
        this.details = Objects.requireNonNull(details, "details");
        this.refunds = new ArrayList<>(refunds);
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        this.version = version;
        if (amount.isZero()) {
            throw new DomainValidationException("Amount must be greater than zero cents");
        }
    }

    // Card: the gateway answers synchronously, so the payment is born AUTHORIZED or DECLINED.
    public static Payment card(UUID id, UUID merchantId, Money amount, CardToken token,
            GatewayClient.CardAuthorization authorization, PaymentDetails details, Instant now) {
        PaymentStatus status = authorization.approved() ? PaymentStatus.AUTHORIZED : PaymentStatus.DECLINED;
        Payment payment = new Payment(id, merchantId, amount, PaymentMethod.CARD, status, token.value(),
                authorization.cardLast4(), null, authorization.gatewayReference(), details, List.of(), now, now, 0);
        payment.record(authorization.approved() ? PaymentEventType.AUTHORIZED : PaymentEventType.DECLINED, null, now);
        return payment;
    }

    // PIX: the buyer still has to pay the QR Code, so the payment is born PENDING.
    public static Payment pix(UUID id, UUID merchantId, Money amount, GatewayClient.PixCharge charge,
            PaymentDetails details, Instant now) {
        Payment payment = new Payment(id, merchantId, amount, PaymentMethod.PIX, PaymentStatus.PENDING, null, null,
                charge.qrCode(), charge.gatewayReference(), details, List.of(), now, now, 0);
        payment.record(PaymentEventType.PENDING, null, now);
        return payment;
    }

    // Rebuilds a stored payment; no events.
    public static Payment restore(UUID id, UUID merchantId, Money amount, PaymentMethod method, PaymentStatus status,
            String cardToken, String cardLast4, String pixQrCode, String gatewayReference, PaymentDetails details,
            List<Refund> refunds, Instant createdAt, Instant updatedAt, long version) {
        return new Payment(id, merchantId, amount, method, status, cardToken, cardLast4, pixQrCode, gatewayReference,
                details, refunds, createdAt, updatedAt, version);
    }

    public void confirmPix(Instant now) {
        if (method != PaymentMethod.PIX || status != PaymentStatus.PENDING) {
            throw new InvalidPaymentTransitionException(
                    "Only PENDING PIX payments can be confirmed; this one is " + method + " " + status);
        }
        status = PaymentStatus.CONFIRMED;
        updatedAt = now;
        record(PaymentEventType.CONFIRMED, null, now);
    }

    public void cancel(Instant now) {
        if (status != PaymentStatus.PENDING) {
            throw new InvalidPaymentTransitionException(
                    "Only PENDING payments can be canceled; this one is " + status + (status.isRefundable() ? ", refund it instead" : ""));
        }
        status = PaymentStatus.CANCELED;
        updatedAt = now;
        record(PaymentEventType.CANCELED, null, now);
    }

    // null = everything that is still refundable. Checked before calling the gateway, so nothing is sent for invalid refunds.
    public Money refundAmountFor(Long requestedCents) {
        if (!status.isRefundable()) {
            throw new InvalidPaymentTransitionException(
                    "Only AUTHORIZED, CONFIRMED or PARTIALLY_REFUNDED payments can be refunded; this one is " + status);
        }
        Money refundable = refundableAmount();
        Money requested = requestedCents == null ? refundable : Money.positive(requestedCents, amount.currency());
        if (requested.isGreaterThan(refundable)) {
            throw new DomainValidationException(
                    "Refund of " + requested.cents() + " cents exceeds the refundable balance of " + refundable.cents() + " cents");
        }
        return requested;
    }

    public Refund refund(UUID refundId, Long requestedCents, String reason, Instant now) {
        Refund refund = new Refund(refundId, refundAmountFor(requestedCents), reason, now);
        refunds.add(refund);
        status = refundableAmount().isZero() ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED;
        updatedAt = now;
        record(PaymentEventType.REFUNDED, refund, now);
        return refund;
    }

    public void changeDetails(PaymentDetails newDetails, Instant now) {
        details = Objects.requireNonNull(newDetails);
        updatedAt = now;
    }

    public Money refundedAmount() {
        long cents = refunds.stream().mapToLong(refund -> refund.amount().cents()).sum();
        return Money.of(cents, amount.currency());
    }

    public Money refundableAmount() {
        return amount.minus(refundedAmount());
    }

    public List<PaymentEvent> pullEvents() {
        List<PaymentEvent> events = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return events;
    }

    private void record(PaymentEventType type, Refund refund, Instant now) {
        pendingEvents.add(new PaymentEvent(UUID.randomUUID(), type, now, id, merchantId, method, status, amount,
                refundedAmount().cents(), refund));
    }

    public UUID id() {
        return id;
    }

    public UUID merchantId() {
        return merchantId;
    }

    public Money amount() {
        return amount;
    }

    public PaymentMethod method() {
        return method;
    }

    public PaymentStatus status() {
        return status;
    }

    public String cardToken() {
        return cardToken;
    }

    public String cardLast4() {
        return cardLast4;
    }

    public String pixQrCode() {
        return pixQrCode;
    }

    public String gatewayReference() {
        return gatewayReference;
    }

    public PaymentDetails details() {
        return details;
    }

    public List<Refund> refunds() {
        return List.copyOf(refunds);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public long version() {
        return version;
    }

    @Override
    public String toString() {
        return "Payment[id=" + id + ", merchantId=" + merchantId + ", method=" + method + ", status=" + status
                + ", amount=" + amount + "]";
    }
}
