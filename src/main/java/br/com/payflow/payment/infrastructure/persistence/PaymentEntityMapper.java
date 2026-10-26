package br.com.payflow.payment.infrastructure.persistence;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import br.com.payflow.payment.domain.Money;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentDetails;
import br.com.payflow.payment.domain.Refund;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

final class PaymentEntityMapper {

    private static final TypeReference<Map<String, String>> METADATA = new TypeReference<>() {
    };

    private final JsonMapper json;

    PaymentEntityMapper(JsonMapper json) {
        this.json = json;
    }

    Payment toDomain(PaymentEntity entity) {
        var refunds = entity.refunds.stream()
                .map(refund -> new Refund(refund.id, Money.of(refund.amountCents, entity.currency), refund.reason,
                        refund.createdAt))
                .toList();
        Map<String, String> metadata = entity.metadata == null ? Map.of() : json.readValue(entity.metadata, METADATA);
        return Payment.restore(entity.id, entity.merchantId, Money.of(entity.amountCents, entity.currency),
                entity.method, entity.status, entity.cardToken, entity.cardLast4, entity.pixQrCode,
                entity.gatewayReference, new PaymentDetails(entity.description, metadata), refunds,
                entity.createdAt, entity.updatedAt, entity.version);
    }

    PaymentEntity toNewEntity(Payment payment) {
        PaymentEntity entity = new PaymentEntity();
        entity.id = payment.id();
        entity.merchantId = payment.merchantId();
        entity.amountCents = payment.amount().cents();
        entity.currency = payment.amount().currency();
        entity.method = payment.method();
        entity.cardToken = payment.cardToken();
        entity.cardLast4 = payment.cardLast4();
        entity.pixQrCode = payment.pixQrCode();
        entity.gatewayReference = payment.gatewayReference();
        entity.createdAt = payment.createdAt();
        applyChanges(payment, entity);
        return entity;
    }

    // Copies what can change after creation: status, details, timestamp and new refunds (refunds are never edited).
    void applyChanges(Payment payment, PaymentEntity entity) {
        entity.status = payment.status();
        entity.description = payment.details().description();
        entity.metadata = payment.details().metadata().isEmpty() ? null : json.writeValueAsString(payment.details().metadata());
        entity.updatedAt = payment.updatedAt();
        Set<UUID> stored = entity.refunds.stream().map(refund -> refund.id).collect(Collectors.toSet());
        for (Refund refund : payment.refunds()) {
            if (!stored.contains(refund.id())) {
                RefundEntity row = new RefundEntity();
                row.id = refund.id();
                row.payment = entity;
                row.amountCents = refund.amount().cents();
                row.reason = refund.reason();
                row.createdAt = refund.createdAt();
                entity.refunds.add(row);
            }
        }
    }
}
