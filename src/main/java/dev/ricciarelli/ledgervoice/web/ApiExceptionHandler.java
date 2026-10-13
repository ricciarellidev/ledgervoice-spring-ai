package dev.ricciarelli.ledgervoice.web;

import dev.ricciarelli.ledgervoice.domain.BudgetException;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.Locale;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(BudgetException.class)
    ResponseEntity<Object> budget(BudgetException exception, WebRequest request) {
        var status = switch (exception.code()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case PROPOSAL_EXPIRED -> HttpStatus.GONE;
            case AUDIO_TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
            case AUDIO_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case AI_UNAVAILABLE -> HttpStatus.BAD_GATEWAY;
            case INVALID_TRANSACTION, TOOL_LIMIT_REACHED -> HttpStatus.UNPROCESSABLE_CONTENT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(problem(status, exception.code().name(), exception.getMessage(), request));
    }

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    ResponseEntity<Object> invalid(Exception exception, WebRequest request) {
        return ResponseEntity.badRequest().body(problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request validation failed.", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> unexpected(Exception exception, WebRequest request) {
        LOG.error("Unhandled request failure type={}", exception.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                "An unexpected error occurred. Use the correlation ID when reporting this issue.", request));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException exception,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(status, "INVALID_REQUEST", "Request validation failed.", request);
        problem.setProperty("violations", exception.getBindingResult().getFieldErrors().stream()
                .map(e -> new Violation(e.getField(), e.getDefaultMessage())).toList());
        return new ResponseEntity<>(problem, headers, status);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var code = status.is5xxServerError() ? "INTERNAL_ERROR" : status.value() == 413 ? "AUDIO_TOO_LARGE" : "INVALID_REQUEST";
        return new ResponseEntity<>(problem(status, code, "Request could not be processed.", request), headers, status);
    }

    private static ProblemDetail problem(HttpStatusCode status, String code, String detail, WebRequest request) {
        var result = ProblemDetail.forStatusAndDetail(status, detail);
        result.setType(URI.create("urn:ledgervoice:problem:" + code.toLowerCase(Locale.ROOT).replace('_', '-')));
        if (request instanceof ServletWebRequest servlet) result.setInstance(URI.create(servlet.getRequest().getRequestURI()));
        result.setProperty("code", code);
        result.setProperty("correlationId", MDC.get("correlationId"));
        return result;
    }

    private record Violation(String field, String message) { }
}
