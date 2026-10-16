package com.codepath.academy.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerMapping;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RequestTelemetryFilter extends OncePerRequestFilter {
    private final MeterRegistry registry;
    private static final Map<String, String> AUTH_OPERATIONS = Map.of(
            "/api/auth/login", "password",
            "/api/auth/passkeys/verify", "passkey",
            "/api/auth/oauth2/exchange", "oauth",
            "/api/auth/register/verify", "registration");

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                  FilterChain chain) throws ServletException, IOException {
        long start = System.nanoTime();
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            int status = failed ? 500 : response.getStatus();
            String operation = AUTH_OPERATIONS.get(request.getRequestURI());
            if (operation != null && "POST".equals(request.getMethod())) {
                registry.counter("codepath.auth.requests", "operation", operation,
                        "outcome", status < 400 ? "success" : "failure").increment();
            }
            Object pattern = request.getAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE);
            // Never log raw paths, query strings, headers, principals or payloads.
            log.info("HTTP method={} route={} status={} duration_ms={}", request.getMethod(),
                    pattern == null ? "unmatched" : pattern, status,
                    (System.nanoTime() - start) / 1_000_000);
        }
    }
}
