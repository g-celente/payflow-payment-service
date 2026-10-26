package br.com.payflow.payment.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;

import br.com.payflow.payment.api.ApiSecurity;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.web.filter.OncePerRequestFilter;

// Every /v1 route answers only the BFF: X-Internal-Key (401 otherwise) plus the merchant it authenticated
// in X-Merchant-Id (400 when missing or not a UUID).
public final class PlatformCallerFilter extends OncePerRequestFilter {

    private final byte[] expectedKey;

    public PlatformCallerFilter(String internalKey) {
        this.expectedKey = internalKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().substring(request.getContextPath().length()).startsWith("/v1/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String key = request.getHeader(ApiSecurity.INTERNAL_KEY_HEADER);
        // Constant-time comparison, so response timing does not reveal how much of the key matched.
        if (key == null || !MessageDigest.isEqual(expectedKey, key.getBytes(StandardCharsets.UTF_8))) {
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "ApiKey header=\"" + ApiSecurity.INTERNAL_KEY_HEADER + "\"");
            ProblemResponses.write(response, 401, "Unauthorized", "Missing or invalid internal key");
            return;
        }
        if (!isUuid(request.getHeader(ApiSecurity.MERCHANT_ID_HEADER))) {
            ProblemResponses.write(response, 400, "Bad Request", "X-Merchant-Id header must be the merchant UUID");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean isUuid(String value) {
        if (value == null) {
            return false;
        }
        try {
            UUID.fromString(value);
            return value.length() == 36;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }
}
