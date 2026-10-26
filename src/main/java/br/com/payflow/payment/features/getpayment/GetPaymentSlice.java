package br.com.payflow.payment.features.getpayment;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class GetPaymentSlice {

    @Bean
    GetPaymentHandler getPaymentHandler(PaymentReader payments) {
        return new GetPaymentHandler(payments);
    }
}
