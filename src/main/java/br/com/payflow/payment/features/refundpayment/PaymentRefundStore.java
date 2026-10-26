package br.com.payflow.payment.features.refundpayment;

import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;

// Output port used only by the refund slice.
public interface PaymentRefundStore {

    Optional<Payment> findById(UUID merchantId, UUID paymentId);

    // Stores the new status, the refund and the payment.refunded event in one transaction.
    // Throws ConcurrentPaymentUpdateException if another refund landed first.
    void save(Payment payment);
}
