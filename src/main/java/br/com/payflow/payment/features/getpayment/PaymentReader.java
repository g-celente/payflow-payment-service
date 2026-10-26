package br.com.payflow.payment.features.getpayment;

import java.util.Optional;
import java.util.UUID;

import br.com.payflow.payment.domain.Payment;

// Output port used only by the get slice. Empty when the payment belongs to another merchant.
public interface PaymentReader {

    Optional<Payment> findById(UUID merchantId, UUID paymentId);
}
