package br.com.payflow.payment.infrastructure.persistence;

import java.util.LinkedHashMap;
import java.util.Map;

import br.com.payflow.payment.domain.PaymentEvent;
import tools.jackson.databind.json.JsonMapper;

// CloudEvents 1.0, structured mode (docs/asyncapi.yaml). merchantid is an extension attribute so consumers
// can route without opening data.
final class CloudEventFactory {

    static final String SOURCE = "/payflow/payment-service";

    private final JsonMapper json;

    CloudEventFactory(JsonMapper json) {
        this.json = json;
    }

    String toJson(PaymentEvent event) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", event.paymentId().toString());
        data.put("merchantId", event.merchantId().toString());
        data.put("method", event.method().name());
        data.put("status", event.status().name());
        data.put("amount", event.amount().cents());
        data.put("currency", event.amount().currency());
        data.put("refundedAmount", event.refundedCents());
        if (event.refund() != null) {
            Map<String, Object> refund = new LinkedHashMap<>();
            refund.put("id", event.refund().id().toString());
            refund.put("amount", event.refund().amount().cents());
            refund.put("reason", event.refund().reason());
            data.put("refund", refund);
        }
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("specversion", "1.0");
        envelope.put("id", event.id().toString());
        envelope.put("source", SOURCE);
        envelope.put("type", event.type().wireName());
        envelope.put("subject", event.paymentId().toString());
        envelope.put("time", event.occurredAt().toString());
        envelope.put("datacontenttype", "application/json");
        envelope.put("merchantid", event.merchantId().toString());
        envelope.put("data", data);
        return json.writeValueAsString(envelope);
    }
}
