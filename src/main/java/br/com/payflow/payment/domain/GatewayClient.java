package br.com.payflow.payment.domain;

import java.util.UUID;

// Port to the (simulated) payment gateway, shared by the slices that move money.
// Implementations throw GatewayTimeoutException or GatewayUnavailableException; a decline is a normal answer.
public interface GatewayClient {

    CardAuthorization authorizeCard(UUID paymentId, Money amount, CardToken token);

    PixCharge createPixCharge(UUID paymentId, Money amount);

    void refund(String gatewayReference, UUID refundId, Money amount);

    record CardAuthorization(boolean approved, String gatewayReference, String cardLast4) {
    }

    record PixCharge(String gatewayReference, String qrCode) {
    }
}
