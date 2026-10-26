package br.com.payflow.payment.features.confirmpixpayment;

import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class ConfirmPixPaymentEndpoint {

    private final ConfirmPixPaymentHandler handler;

    ConfirmPixPaymentEndpoint(ConfirmPixPaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/v1/payments/{id}/pix-confirmation")
    @Operation(summary = "Simula a confirmação do PIX pelo gateway (PENDING → CONFIRMED)")
    PaymentResponse confirm(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId, @PathVariable UUID id) {
        return PaymentResponse.from(handler.execute(merchantId, id));
    }
}
