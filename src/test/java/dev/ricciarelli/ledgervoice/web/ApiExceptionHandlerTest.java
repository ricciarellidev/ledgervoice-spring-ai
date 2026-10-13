package dev.ricciarelli.ledgervoice.web;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.net.URI;

import static org.assertj.core.api.Assertions.*;

class ApiExceptionHandlerTest {
    private final ApiExceptionHandler handler = new ApiExceptionHandler();
    private final ServletWebRequest request = new ServletWebRequest(new MockHttpServletRequest("POST", "/api/v1/proposals"));

    @Test
    void unexpectedFailuresDoNotExposeDiagnostics() {
        MDC.put("correlationId", "error_test_001");
        try {
            var response = handler.unexpected(new IllegalStateException("Sensitive internal diagnostic"), request);
            var problem = (ProblemDetail) response.getBody();
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
            assertThat(problem.getDetail()).doesNotContain("Sensitive", "diagnostic");
            assertThat(problem.getInstance()).isEqualTo(URI.create("/api/v1/proposals"));
            assertThat(problem.getProperties()).containsEntry("code", "INTERNAL_ERROR").containsEntry("correlationId", "error_test_001");
        } finally {
            MDC.remove("correlationId");
        }
    }

    @Test
    void classifiesFrameworkErrorsAndSanitizesBadRequests() {
        var framework = handler.handleExceptionInternal(new IllegalStateException("Internal diagnostic"),
                "Sensitive body", HttpHeaders.EMPTY, HttpStatus.INTERNAL_SERVER_ERROR, request);
        assertThat(((ProblemDetail) framework.getBody()).getProperties()).containsEntry("code", "INTERNAL_ERROR");
        var invalid = handler.invalid(new IllegalArgumentException("Sensitive argument"), request);
        assertThat(invalid.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(((ProblemDetail) invalid.getBody()).getDetail()).doesNotContain("Sensitive");
    }
}
