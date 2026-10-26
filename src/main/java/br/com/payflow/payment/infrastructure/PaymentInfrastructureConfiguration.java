package br.com.payflow.payment.infrastructure;

import java.time.Clock;

import br.com.payflow.payment.infrastructure.gateway.GatewayProperties;
import br.com.payflow.payment.infrastructure.gateway.ResilientGatewayClient;
import br.com.payflow.payment.infrastructure.gateway.SimulatedGatewayClient;
import br.com.payflow.payment.infrastructure.persistence.JpaPaymentStore;
import br.com.payflow.payment.infrastructure.persistence.PaymentJpaRepository;
import jakarta.persistence.EntityManager;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

// Binds the slice ports and the GatewayClient port to their adapters.
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(GatewayProperties.class)
public class PaymentInfrastructureConfiguration {

    @Bean
    JpaPaymentStore paymentStore(PaymentJpaRepository repository, EntityManager entityManager, JsonMapper json) {
        return new JpaPaymentStore(repository, entityManager, json);
    }

    @Bean(destroyMethod = "close")
    ResilientGatewayClient gatewayClient(GatewayProperties properties) {
        return new ResilientGatewayClient(new SimulatedGatewayClient(properties.simulatedDelay()),
                properties.timeout(), properties.circuitOpenDuration());
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
