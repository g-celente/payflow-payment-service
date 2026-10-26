package br.com.payflow.payment.infrastructure.gateway;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// payflow.gateway.*: timeout per call, how long tok_timeout makes the simulated gateway wait,
// and how long the circuit stays open after repeated failures.
@ConfigurationProperties("payflow.gateway")
public record GatewayProperties(
        @DefaultValue("2s") Duration timeout,
        @DefaultValue("5s") Duration simulatedDelay,
        @DefaultValue("30s") Duration circuitOpenDuration) {
}
