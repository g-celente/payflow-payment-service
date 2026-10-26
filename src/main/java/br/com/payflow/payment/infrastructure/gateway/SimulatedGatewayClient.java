package br.com.payflow.payment.infrastructure.gateway;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import br.com.payflow.payment.domain.CardToken;
import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.GatewayTimeoutException;
import br.com.payflow.payment.domain.Money;

// Stand-in for the external gateway. Card outcome comes from the token:
// tok_declined -> declined, tok_timeout -> answers only after simulatedDelay (longer than the timeout),
// any other tok_* -> approved. A token ending in _NNNN (tok_visa_1234) reports those digits as last4.
public final class SimulatedGatewayClient implements GatewayClient {

    static final String DECLINED_TOKEN = "tok_declined";
    static final String TIMEOUT_TOKEN = "tok_timeout";
    private static final Pattern LAST4 = Pattern.compile(".*_(\\d{4})$");

    private final Duration simulatedDelay;
    private final SecureRandom random = new SecureRandom();

    public SimulatedGatewayClient(Duration simulatedDelay) {
        this.simulatedDelay = simulatedDelay;
    }

    @Override
    public CardAuthorization authorizeCard(UUID paymentId, Money amount, CardToken token) {
        if (TIMEOUT_TOKEN.equals(token.value())) {
            sleep(simulatedDelay);
        }
        boolean approved = !DECLINED_TOKEN.equals(token.value());
        return new CardAuthorization(approved, reference("ch"), last4(token));
    }

    @Override
    public PixCharge createPixCharge(UUID paymentId, Money amount) {
        return new PixCharge(reference("pix"), PixCopyPaste.of(paymentId, amount));
    }

    @Override
    public void refund(String gatewayReference, UUID refundId, Money amount) {
        // Always accepted by the simulated gateway.
    }

    private String reference(String kind) {
        byte[] bytes = new byte[12];
        random.nextBytes(bytes);
        return "gw_" + kind + "_" + HexFormat.of().formatHex(bytes);
    }

    private static String last4(CardToken token) {
        Matcher matcher = LAST4.matcher(token.value());
        return matcher.matches() ? matcher.group(1) : "4242";
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration);
        } catch (InterruptedException exception) {
            // The resilience layer cancels the call when it times out.
            Thread.currentThread().interrupt();
            throw new GatewayTimeoutException();
        }
    }

    // "PIX copia e cola" (BR Code / EMV) with the payment id as the random key. Valid format and CRC, fake account.
    static final class PixCopyPaste {

        private PixCopyPaste() {
        }

        static String of(UUID paymentId, Money amount) {
            String account = field("00", "br.gov.bcb.pix") + field("01", paymentId.toString());
            String value = String.format(Locale.ROOT, "%d.%02d", amount.cents() / 100, amount.cents() % 100);
            String payload = field("00", "01") + field("26", account) + field("52", "0000") + field("53", "986")
                    + field("54", value) + field("58", "BR") + field("59", "PAYFLOW DEMO") + field("60", "CURITIBA")
                    + field("62", field("05", "***")) + "6304";
            return payload + crc16(payload);
        }

        private static String field(String id, String value) {
            return id + String.format(Locale.ROOT, "%02d", value.length()) + value;
        }

        // CRC-16/CCITT-FALSE, as required by the BR Code specification.
        static String crc16(String payload) {
            int crc = 0xFFFF;
            for (byte b : payload.getBytes(StandardCharsets.UTF_8)) {
                crc ^= (b & 0xFF) << 8;
                for (int bit = 0; bit < 8; bit++) {
                    crc = (crc & 0x8000) != 0 ? (crc << 1) ^ 0x1021 : crc << 1;
                }
            }
            return String.format(Locale.ROOT, "%04X", crc & 0xFFFF);
        }
    }
}
