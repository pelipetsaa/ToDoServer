package com.pelypets.todolist.server.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Gives every request an id, taken from X-Request-Id when the client sends a plain token (letters, digits, {@code ._:-};
 * anything else could forge log lines or headers), puts it in the logging MDC
 * and echoes it in the response, and logs one line per API call.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    public static final String MDC_KEY = "requestId";
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9._:-]{1,64}");
    private static final Logger log = LoggerFactory.getLogger(RequestIdFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String requestId = requestIdOf(request);
        response.setHeader(HEADER, requestId);
        MDC.put(MDC_KEY, requestId);
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            if (request.getRequestURI().startsWith("/api")) {
                log.info("{} {} -> {} in {} ms", request.getMethod(), request.getRequestURI(), response.getStatus(),
                        (System.nanoTime() - started) / 1_000_000);
            }
            MDC.remove(MDC_KEY);
        }
    }

    private static String requestIdOf(HttpServletRequest request) {
        String sent = request.getHeader(HEADER);
        if (sent != null && SAFE_ID.matcher(sent).matches()) return sent;
        return UUID.randomUUID().toString();
    }
}
