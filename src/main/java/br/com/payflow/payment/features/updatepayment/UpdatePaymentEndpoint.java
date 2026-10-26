package br.com.payflow.payment.features.updatepayment;

import java.util.Map;
import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class UpdatePaymentEndpoint {

    private final UpdatePaymentHandler handler;

    UpdatePaymentEndpoint(UpdatePaymentHandler handler) {
        this.handler = handler;
    }

    @PatchMapping("/v1/payments/{id}")
    @Operation(summary = "Altera descrição e metadados (campo ausente fica como está)")
    PaymentResponse update(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId, @PathVariable UUID id,
            @RequestBody UpdatePaymentRequest request) {
        return PaymentResponse.from(handler.execute(
                new UpdatePaymentCommand(merchantId, id, request.description(), request.metadata())));
    }

    record UpdatePaymentRequest(String description, Map<String, String> metadata) {
    }
}
