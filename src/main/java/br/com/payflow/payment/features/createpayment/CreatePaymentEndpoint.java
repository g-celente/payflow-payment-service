package br.com.payflow.payment.features.createpayment;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import br.com.payflow.payment.domain.Payment;
import br.com.payflow.payment.domain.PaymentMethod;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class CreatePaymentEndpoint {

    private final CreatePaymentHandler handler;

    CreatePaymentEndpoint(CreatePaymentHandler handler) {
        this.handler = handler;
    }

    @PostMapping("/v1/payments")
    @Operation(summary = "Cria um pagamento CARD (tok_approved, tok_declined, tok_timeout) ou PIX",
            description = "Cartão volta AUTHORIZED ou DECLINED; PIX volta PENDING com o QR Code. "
                    + "Repetir com o mesmo Idempotency-Key devolve a resposta original.")
    @Parameter(name = ApiSecurity.IDEMPOTENCY_KEY_HEADER, in = ParameterIn.HEADER, required = true,
            description = "Chave única por tentativa de cobrança (até 255 caracteres)")
    ResponseEntity<PaymentResponse> create(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId,
            @RequestBody CreatePaymentRequest request) {
        Payment payment = handler.execute(new CreatePaymentCommand(merchantId, request.amount(), request.currency(),
                request.method(), request.cardToken(), request.description(), request.metadata()));
        return ResponseEntity.created(URI.create("/v1/payments/" + payment.id())).body(PaymentResponse.from(payment));
    }

    record CreatePaymentRequest(Long amount, String currency, PaymentMethod method, String cardToken,
            String description, Map<String, String> metadata) {
    }
}
