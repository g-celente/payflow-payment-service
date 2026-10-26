package br.com.payflow.payment.features.listpayments;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import br.com.payflow.payment.api.PaymentResponse;
import br.com.payflow.payment.domain.PaymentMethod;
import br.com.payflow.payment.domain.PaymentStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "Pagamentos")
class ListPaymentsEndpoint {

    private final ListPaymentsHandler handler;

    ListPaymentsEndpoint(ListPaymentsHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/v1/payments")
    @Operation(summary = "Lista os pagamentos do lojista, mais recentes primeiro",
            description = "Filtros opcionais: status, method e período de criação (from inclusivo, to exclusivo, ISO-8601).")
    PaymentPageResponse list(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) PaymentMethod method,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaymentPage result = handler.execute(new ListPaymentsQuery(merchantId, status, method, from, to, page, size));
        return new PaymentPageResponse(result.items().stream().map(PaymentResponse::from).toList(),
                result.page(), result.size(), result.totalItems(), result.totalPages());
    }

    record PaymentPageResponse(List<PaymentResponse> items, int page, int size, long totalItems, long totalPages) {
    }
}
