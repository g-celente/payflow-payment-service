package br.com.payflow.payment.features.paymentsummary;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
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
class PaymentSummaryEndpoint {

    private final PaymentSummaryHandler handler;

    PaymentSummaryEndpoint(PaymentSummaryHandler handler) {
        this.handler = handler;
    }

    @GetMapping("/v1/payments/summary")
    @Operation(summary = "Totais do lojista para o painel",
            description = "count e byStatus contam todos os pagamentos; totalAmount e byMethod somam o volume aprovado "
                    + "líquido de estornos, em centavos (base do cálculo de taxas).")
    SummaryResponse summary(@RequestHeader(ApiSecurity.MERCHANT_ID_HEADER) UUID merchantId,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        PaymentSummary summary = handler.execute(new PaymentSummaryQuery(merchantId, from, to));
        return new SummaryResponse(summary.currency(), summary.totalAmount(), summary.count(),
                summary.byStatus(), summary.byMethod());
    }

    record SummaryResponse(String currency, long totalAmount, long count,
            Map<PaymentStatus, Long> byStatus, Map<PaymentMethod, Long> byMethod) {
    }
}
