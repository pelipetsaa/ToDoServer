package com.pelypets.todolist.server.adapter.in.web.security;

import com.pelypets.todolist.server.adapter.in.web.error.ErrorCode;
import com.pelypets.todolist.server.adapter.in.web.error.ErrorResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Guards the Prometheus endpoint. With a token the scraper must send it as a bearer token; without one the endpoint
 * is open in development and does not exist in production.
 */
public class MetricsEndpointGuard extends OncePerRequestFilter {

    private final String path;
    private final Optional<byte[]> expectedHeader;
    private final boolean hiddenWithoutToken;
    private final ErrorResponseWriter writer;

    public MetricsEndpointGuard(String path, Optional<String> token, boolean production, ErrorResponseWriter writer) {
        this.path = path;
        this.expectedHeader = token.map(it -> ("Bearer " + it).getBytes(StandardCharsets.UTF_8));
        this.hiddenWithoutToken = production;
        this.writer = writer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().equals(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (expectedHeader.isEmpty()) {
            if (hiddenWithoutToken) {
                writer.write(response, HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, "Not Found");
                return;
            }
        } else if (!matches(request.getHeader(HttpHeaders.AUTHORIZATION))) {
            writer.write(response, HttpStatus.UNAUTHORIZED, ErrorCode.UNAUTHORIZED, "Metrics token is missing or invalid");
            return;
        }
        chain.doFilter(request, response);
    }

    /** Constant-time comparison, so response timing does not reveal how much of the token matched. */
    private boolean matches(String sent) {
        return sent != null && MessageDigest.isEqual(expectedHeader.orElseThrow(), sent.getBytes(StandardCharsets.UTF_8));
    }
}
