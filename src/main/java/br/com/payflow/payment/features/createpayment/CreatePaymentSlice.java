package br.com.payflow.payment.features.createpayment;

import java.time.Clock;

import br.com.payflow.payment.domain.GatewayClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CreatePaymentSlice {

    @Bean
    CreatePaymentHandler createPaymentHandler(NewPaymentStore payments, GatewayClient gateway, Clock clock) {
        return new CreatePaymentHandler(payments, gateway, clock);
    }
}
