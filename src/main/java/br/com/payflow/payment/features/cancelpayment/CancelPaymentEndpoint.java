package br.com.payflow.payment.features.cancelpayment;

import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class CancelPaymentEndpoint {

    private final CancelPaymentHandler handler;

    CancelPaymentEndpoint(CancelPaymentHandler handler) {
        this.handler = handler;
    }

    @DeleteMapping("/v1/payments/{id}")
    @Operation(summary = "Cancela um pagamento PENDING (o registro fica, com status CANCELED)")
    PaymentResponse cancel(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId, @PathVariable UUID id) {
        return PaymentResponse.from(handler.execute(merchantId, id));
    }
}
