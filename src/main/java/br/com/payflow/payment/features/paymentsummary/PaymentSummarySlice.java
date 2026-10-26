package br.com.payflow.payment.features.paymentsummary;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class PaymentSummarySlice {

    @Bean
    PaymentSummaryHandler paymentSummaryHandler(PaymentStatistics statistics) {
        return new PaymentSummaryHandler(statistics);
    }
}
