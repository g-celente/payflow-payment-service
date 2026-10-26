package br.com.payflow.payment.support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import br.com.payflow.payment.domain.CardToken;
import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.GatewayTimeoutException;
import br.com.payflow.payment.domain.Money;

// tok_declined declines, tok_timeout times out, anything else is approved. Records every refund call.
public final class FakeGateway implements GatewayClient {

    private final List<Money> refunds = new ArrayList<>();

    public List<Money> refunds() {
        return List.copyOf(refunds);
    }

    @Override
    public CardAuthorization authorizeCard(UUID paymentId, Money amount, CardToken token) {
        if ("tok_timeout".equals(token.value())) {
            throw new GatewayTimeoutException();
        }
        return new CardAuthorization(!"tok_declined".equals(token.value()), "gw_ch_" + paymentId, "4242");
    }

    @Override
    public PixCharge createPixCharge(UUID paymentId, Money amount) {
        return new PixCharge("gw_pix_" + paymentId, "000201-pix-" + paymentId);
    }

    @Override
    public void refund(String gatewayReference, UUID refundId, Money amount) {
        refunds.add(amount);
    }
}
