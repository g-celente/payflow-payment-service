package br.com.payflow.payment.features.updatepayment;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class UpdatePaymentSlice {

    @Bean
    UpdatePaymentHandler updatePaymentHandler(PaymentDetailsStore payments, Clock clock) {
        return new UpdatePaymentHandler(payments, clock);
    }
}
