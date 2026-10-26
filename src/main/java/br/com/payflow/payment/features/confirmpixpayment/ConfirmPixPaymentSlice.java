package br.com.payflow.payment.features.confirmpixpayment;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ConfirmPixPaymentSlice {

    @Bean
    ConfirmPixPaymentHandler confirmPixPaymentHandler(PixConfirmationStore payments, Clock clock) {
        return new ConfirmPixPaymentHandler(payments, clock);
    }
}
