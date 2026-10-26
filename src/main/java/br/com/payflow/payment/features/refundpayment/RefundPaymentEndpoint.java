package br.com.payflow.payment.features.refundpayment;

import java.net.URI;
import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import br.com.payflow.payment.domain.Payment;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class RefundPaymentEndpoint {

    private final RefundPaymentHandler handler;

    RefundPaymentEndpoint(RefundPaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/v1/payments/{id}/refunds")
    @Operation(summary = "Estorno total (sem amount) ou parcial; devolve o pagamento com os estornos")
    @Parameter(name = ApiSecurity.IDEMPOTENCY_KEY_HEADER, in = ParameterIn.HEADER, required = true,
            description = "Chave única por tentativa de estorno (até 255 caracteres)")
    ResponseEntity<PaymentResponse> refund(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId,
            @PathVariable UUID id, @RequestBody(required = false) RefundRequest request) {
        RefundRequest body = request == null ? new RefundRequest(null, null) : request;
        Payment payment = handler.execute(new RefundPaymentCommand(merchantId, id, body.amount(), body.reason()));
        return ResponseEntity.created(URI.create("/v1/payments/" + payment.id())).body(PaymentResponse.from(payment));
    }

    record RefundRequest(Long amount, String reason) {
    }
}
