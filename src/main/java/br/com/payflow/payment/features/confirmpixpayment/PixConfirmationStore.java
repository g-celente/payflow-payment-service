package br.com.payflow.payment.features.confirmpixpayment;

import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;

// Output port used only by the PIX confirmation slice.
public interface PixConfirmationStore {

    Optional<Payment> findById(UUID merchantId, UUID paymentId);

    // Stores the new status and the payment.confirmed event in one transaction.
    void save(Payment payment);
}
