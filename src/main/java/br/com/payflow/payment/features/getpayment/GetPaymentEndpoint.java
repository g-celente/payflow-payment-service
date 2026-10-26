package br.com.payflow.payment.features.getpayment;

import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class GetPaymentEndpoint {

    private final GetPaymentHandler handler;

    GetPaymentEndpoint(GetPaymentHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/v1/payments/{id}")
    @Operation(summary = "Detalha um pagamento, com os estornos")
    PaymentResponse get(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId, @PathVariable UUID id) {
        return PaymentResponse.from(handler.execute(merchantId, id));
    }
}
