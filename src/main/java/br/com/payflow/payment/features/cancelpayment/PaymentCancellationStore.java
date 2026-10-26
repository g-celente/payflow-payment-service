package br.com.payflow.payment.features.cancelpayment;

import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;

// Output port used only by the cancel slice.
public interface PaymentCancellationStore {

    Optional<Payment> findById(UUID merchantId, UUID paymentId);

    // Stores the new status and the payment.canceled event in one transaction.
    void save(Payment payment);
}
