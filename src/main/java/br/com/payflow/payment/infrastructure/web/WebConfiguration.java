package br.com.payflow.payment.infrastructure.web;

import java.time.Clock;
import java.time.Duration;

import br.com.payflow.payment.api.ApiSecurity;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(InternalKeyProperties.class)
public class WebConfiguration {

    // First: reject callers that are not the BFF.
    @Bean
    FilterRegistrationBean<PlatformCallerFilter> platformCallerFilter(InternalKeyProperties properties) {
        var registration = new FilterRegistrationBean<>(new PlatformCallerFilter(properties.internalKey()));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    // Then: replay or reserve the Idempotency-Key of create and refund requests.
    @Bean
    FilterRegistrationBean<IdempotencyFilter> idempotencyFilter(JdbcTemplate jdbc, Clock clock,
            @Value("${payflow.idempotency.ttl:24h}") Duration ttl) {
        var registration = new FilterRegistrationBean<>(new IdempotencyFilter(new JdbcIdempotencyStore(jdbc), ttl, clock));
        registration.addUrlPatterns("/v1/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }

    // Swagger at /swagger-ui.html; every route needs the internal key and X-Merchant-Id.
    @Bean
    OpenAPI paymentServiceOpenApi() {
        return new OpenAPI()
                .info(new Info().title("PayFlow payment-service").version("v1")
                        .description("Domínio de pagamentos do PayFlow: cartão e PIX, estornos, idempotência e "
                                + "eventos na fila payment-events (contrato em docs/asyncapi.yaml)."))
                .components(new Components().addSecuritySchemes(ApiSecurity.INTERNAL_KEY_SCHEME,
                        new SecurityScheme().type(SecurityScheme.Type.APIKEY).in(SecurityScheme.In.HEADER)
                                .name(ApiSecurity.INTERNAL_KEY_HEADER)))
                .addSecurityItem(new SecurityRequirement().addList(ApiSecurity.INTERNAL_KEY_SCHEME));
    }
}
