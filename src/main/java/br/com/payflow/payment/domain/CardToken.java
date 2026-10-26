package br.com.payflow.payment.domain;

import java.util.regex.Pattern;

// Token issued by the gateway's tokenization; the PayFlow never receives card numbers.
// Simulated tokens: tok_approved, tok_declined, tok_timeout (any other tok_* is approved).
public record CardToken(String value) {

    private static final Pattern FORMAT = Pattern.compile("tok_[A-Za-z0-9_]{1,60}");

    public CardToken {
        if (value == null || !FORMAT.matcher(value).matches()) {
            throw new DomainValidationException("cardToken must look like tok_approved (tok_ + letters, digits or _)");
        }
    }
}
