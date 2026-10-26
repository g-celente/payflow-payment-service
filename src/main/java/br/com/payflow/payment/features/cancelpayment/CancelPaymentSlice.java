package br.com.payflow.payment.features.cancelpayment;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class CancelPaymentSlice {

    @Bean
    CancelPaymentHandler cancelPaymentHandler(PaymentCancellationStore payments, Clock clock) {
        return new CancelPaymentHandler(payments, clock);
    }
}
