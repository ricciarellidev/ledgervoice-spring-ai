package dev.ricciarelli.ledgervoice.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var id = request.getHeader("X-Correlation-Id");
        if (id == null || !id.matches("[A-Za-z0-9._-]{1,100}")) id = UUID.randomUUID().toString();
        MDC.put("correlationId", id);
        response.setHeader("X-Correlation-Id", id);
        response.setHeader("Cache-Control", "no-store");
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove("correlationId");
        }
    }
}
