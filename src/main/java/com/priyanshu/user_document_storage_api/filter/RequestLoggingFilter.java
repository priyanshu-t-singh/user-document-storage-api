package com.priyanshu.user_document_storage_api.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Servlet filter that logs a single structured line per HTTP request,
 * including method, URI, query string, response status, and elapsed time.
 *
 * <p>Actuator ({@code /actuator/**}) and Swagger UI ({@code /swagger-ui/**},
 * {@code /v3/api-docs/**}) requests are excluded from logging to keep the
 * logs focused on application traffic.
 */
@Slf4j
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String[] EXCLUDED_PREFIXES = {
            "/actuator",
            "/swagger-ui",
            "/v3/api-docs"
    };

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        for (String prefix : EXCLUDED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        long startTime = System.currentTimeMillis();

        try {
            filterChain.doFilter(request, response);
        } finally {
            long durationMs = System.currentTimeMillis() - startTime;
            log.info("{} {} {} | {}ms",
                    request.getMethod(),
                    buildFullPath(request),
                    response.getStatus(),
                    durationMs);
        }
    }

    private String buildFullPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String query = request.getQueryString();
        return (query != null && !query.isBlank()) ? uri + "?" + query : uri;
    }
}
