package br.com.payflow.payment.infrastructure.gateway;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import br.com.payflow.payment.domain.CardToken;
import br.com.payflow.payment.domain.GatewayClient;
import br.com.payflow.payment.domain.GatewayTimeoutException;
import br.com.payflow.payment.domain.GatewayUnavailableException;
import br.com.payflow.payment.domain.Money;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

// Decorator: every gateway call gets a timeout, and repeated failures open a circuit breaker
// that refuses calls for a while instead of piling up slow requests.
public final class ResilientGatewayClient implements GatewayClient, AutoCloseable {

    private final GatewayClient delegate;
    private final TimeLimiter timeLimiter;
    private final CircuitBreaker circuitBreaker;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public ResilientGatewayClient(GatewayClient delegate, Duration timeout, Duration openStateDuration) {
        this.delegate = delegate;
        this.timeLimiter = TimeLimiter.of("payment-gateway", TimeLimiterConfig.custom()
                .timeoutDuration(timeout)
                .cancelRunningFuture(true)
                .build());
        this.circuitBreaker = CircuitBreaker.of("payment-gateway", CircuitBreakerConfig.custom()
                .slidingWindowSize(10)
                .minimumNumberOfCalls(5)
                .failureRateThreshold(50)
                .waitDurationInOpenState(openStateDuration)
                .permittedNumberOfCallsInHalfOpenState(2)
                .build());
    }

    @Override
    public CardAuthorization authorizeCard(UUID paymentId, Money amount, CardToken token) {
        return call(() -> delegate.authorizeCard(paymentId, amount, token));
    }

    @Override
    public PixCharge createPixCharge(UUID paymentId, Money amount) {
        return call(() -> delegate.createPixCharge(paymentId, amount));
    }

    @Override
    public void refund(String gatewayReference, UUID refundId, Money amount) {
        call(() -> {
            delegate.refund(gatewayReference, refundId, amount);
            return null;
        });
    }

    CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }

    private <T> T call(Supplier<T> action) {
        Callable<T> limited = timeLimiter.decorateFutureSupplier(() -> executor.submit(action::get));
        try {
            return circuitBreaker.executeCallable(limited);
        } catch (CallNotPermittedException exception) {
            throw new GatewayUnavailableException();
        } catch (TimeoutException exception) {
            throw new GatewayTimeoutException();
        } catch (ExecutionException exception) {
            throw rethrow(exception.getCause());
        } catch (Exception exception) {
            throw rethrow(exception);
        }
    }

    private static RuntimeException rethrow(Throwable failure) {
        if (failure instanceof RuntimeException runtime) {
            return runtime;
        }
        return new IllegalStateException("Payment gateway call failed", failure);
    }

    @Override
    public void close() {
        executor.shutdownNow();
    }
}
