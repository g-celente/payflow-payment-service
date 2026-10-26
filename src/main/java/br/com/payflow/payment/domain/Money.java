package br.com.payflow.payment.domain;

// Amount in cents. The simulated gateway only works in BRL.
public record Money(long cents, String currency) {

    public static final String BRL = "BRL";
    // R$ 1.000.000,00 per payment.
    public static final long MAX_CENTS = 100_000_000L;

    public Money {
        if (!BRL.equals(currency)) {
            throw new DomainValidationException("Only BRL is supported");
        }
        if (cents < 0 || cents > MAX_CENTS) {
            throw new DomainValidationException("Amount must be between 0 and " + MAX_CENTS + " cents");
        }
    }

    public static Money of(long cents, String currency) {
        return new Money(cents, currency);
    }

    // For new payments and refunds, which must move some money.
    public static Money positive(Long cents, String currency) {
        if (cents == null || cents <= 0) {
            throw new DomainValidationException("Amount must be greater than zero cents");
        }
        return new Money(cents, currency);
    }

    public Money minus(Money other) {
        return new Money(cents - sameCurrency(other).cents, currency);
    }

    public boolean isGreaterThan(Money other) {
        return cents > sameCurrency(other).cents;
    }

    public boolean isZero() {
        return cents == 0;
    }

    private Money sameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new DomainValidationException("Currencies differ: " + currency + " and " + other.currency);
        }
        return other;
    }
}
