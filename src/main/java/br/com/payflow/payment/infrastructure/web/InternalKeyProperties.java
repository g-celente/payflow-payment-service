package br.com.payflow.payment.infrastructure.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// payflow.internal-key (env PAYFLOW_INTERNAL_KEY): shared with the BFF.
@Validated
@ConfigurationProperties("payflow")
public record InternalKeyProperties(
        @NotBlank @Size(min = 32, max = 256) String internalKey) {

    @Override
    public String toString() {
        return "InternalKeyProperties[internalKey=****]";
    }
}
