package br.com.payflow.payment.infrastructure.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;

// Problem Details (RFC 9457) written by the servlet filters, which run before Spring MVC.
// Callers pass constant texts only, so no JSON escaping is needed.
final class ProblemResponses {

    private ProblemResponses() {
    }

    static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        String body = "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
                + ",\"detail\":\"" + detail + "\"}";
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
    }
}
