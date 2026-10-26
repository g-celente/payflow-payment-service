package br.com.payflow.payment.features.listpayments;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ListPaymentsSlice {

    @Bean
    ListPaymentsHandler listPaymentsHandler(PaymentListing payments) {
        return new ListPaymentsHandler(payments);
    }
}
