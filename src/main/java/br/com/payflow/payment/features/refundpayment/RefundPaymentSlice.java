package br.com.payflow.payment.features.refundpayment;

import java.time.Clock;

import br.com.payflow.payment.domain.GatewayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class RefundPaymentSlice {

    @Bean
    RefundPaymentHandler refundPaymentHandler(PaymentRefundStore payments, GatewayClient gateway, Clock clock) {
        return new RefundPaymentHandler(payments, gateway, clock);
    }
}
