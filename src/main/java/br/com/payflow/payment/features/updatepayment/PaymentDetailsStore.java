package br.com.payflow.payment.features.updatepayment;

import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;

// Output port used only by the update slice.
public interface PaymentDetailsStore {

    Optional<Payment> findById(UUID merchantId, UUID paymentId);

    // Throws ConcurrentPaymentUpdateException if the payment changed since it was read.
    void save(Payment payment);
}
