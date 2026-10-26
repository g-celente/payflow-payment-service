package br.com.payflow.payment.infrastructure.web;

import br.com.payflow.payment.domain.ConcurrentPaymentUpdateException;
import br.com.payflow.payment.domain.DomainValidationException;
import br.com.payflow.payment.domain.GatewayTimeoutException;
import br.com.payflow.payment.domain.GatewayUnavailableException;
import br.com.payflow.payment.domain.InvalidPaymentTransitionException;
import br.com.payflow.payment.domain.PaymentNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

// Domain errors as RFC 9457 Problem Details.
@RestControllerAdvice
class ProblemDetailsAdvice {

    static final String RETRY_AFTER_SECONDS = "30";

    @ExceptionHandler(PaymentNotFoundException.class)
    ProblemDetail notFound(PaymentNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, "Not Found", exception.getMessage());
    }

    @ExceptionHandler(DomainValidationException.class)
    ProblemDetail invalid(DomainValidationException exception) {
        return problem(HttpStatus.BAD_REQUEST, "Bad Request", exception.getMessage());
    }

    @ExceptionHandler(InvalidPaymentTransitionException.class)
    ProblemDetail invalidTransition(InvalidPaymentTransitionException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict", exception.getMessage());
    }

    @ExceptionHandler({ConcurrentPaymentUpdateException.class, ObjectOptimisticLockingFailureException.class})
    ProblemDetail concurrentUpdate(RuntimeException exception) {
        return problem(HttpStatus.CONFLICT, "Conflict",
                "The payment was changed by another request; read it again and retry");
    }

    @ExceptionHandler(GatewayTimeoutException.class)
    ProblemDetail gatewayTimeout(GatewayTimeoutException exception) {
        return problem(HttpStatus.GATEWAY_TIMEOUT, "Gateway Timeout", exception.getMessage());
    }

    @ExceptionHandler(GatewayUnavailableException.class)
    ResponseEntity<ProblemDetail> gatewayUnavailable(GatewayUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .body(problem(HttpStatus.SERVICE_UNAVAILABLE, "Service Unavailable", exception.getMessage()));
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
