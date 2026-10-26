package br.com.payflow.payment.support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentEvent;
import br.com.payflow.payment.features.cancelpayment.PaymentCancellationStore;
import br.com.payflow.payment.features.confirmpixpayment.PixConfirmationStore;
import br.com.payflow.payment.features.createpayment.NewPaymentStore;
import br.com.payflow.payment.features.getpayment.PaymentReader;
import br.com.payflow.payment.features.listpayments.ListPaymentsQuery;
import br.com.payflow.payment.features.listpayments.PaymentListing;
import br.com.payflow.payment.features.listpayments.PaymentPage;
import br.com.payflow.payment.features.refundpayment.PaymentRefundStore;
import br.com.payflow.payment.features.updatepayment.PaymentDetailsStore;

// Test double for the slice ports. Like the JPA adapter, every write takes the payment's pending events.
public final class InMemoryPaymentStore implements NewPaymentStore, PaymentReader, PaymentListing, PaymentDetailsStore,
        PaymentCancellationStore, PaymentRefundStore, PixConfirmationStore {

    private final Map<UUID, Payment> payments = new LinkedHashMap<>();
    private final List<PaymentEvent> outbox = new ArrayList<>();
    private int saves;

    public InMemoryPaymentStore with(Payment... initial) {
        for (Payment payment : initial) {
            payment.pullEvents();
            payments.put(payment.id(), payment);
        }
        return this;
    }

    public List<PaymentEvent> outbox() {
        return List.copyOf(outbox);
    }

    public int size() {
        return payments.size();
    }

    public int saves() {
        return saves;
    }

    @Override
    public void add(Payment payment) {
        payments.put(payment.id(), payment);
        outbox.addAll(payment.pullEvents());
    }

    @Override
    public Optional<Payment> findById(UUID merchantId, UUID paymentId) {
        return Optional.ofNullable(payments.get(paymentId)).filter(payment -> payment.merchantId().equals(merchantId));
    }

    @Override
    public void save(Payment payment) {
        saves++;
        payments.put(payment.id(), payment);
        outbox.addAll(payment.pullEvents());
    }

    @Override
    public PaymentPage list(ListPaymentsQuery query) {
        var matching = payments.values().stream()
                .filter(payment -> payment.merchantId().equals(query.merchantId()))
                .filter(payment -> query.status() == null || payment.status() == query.status())
                .filter(payment -> query.method() == null || payment.method() == query.method())
                .sorted(Comparator.comparing(Payment::createdAt).reversed())
                .toList();
        var page = matching.stream().skip((long) query.page() * query.size()).limit(query.size()).toList();
        return new PaymentPage(page, query.page(), query.size(), matching.size());
    }
}
