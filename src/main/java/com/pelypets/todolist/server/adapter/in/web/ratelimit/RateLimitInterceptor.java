package com.pelypets.todolist.server.adapter.in.web.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/** Applies {@link AuthRateLimiter} to handlers annotated with {@link RateLimited}. */
public class RateLimitInterceptor implements HandlerInterceptor {

    private final AuthRateLimiter limiter;
    private final ClientIpResolver clientIpResolver;

    public RateLimitInterceptor(AuthRateLimiter limiter, ClientIpResolver clientIpResolver) {
        this.limiter = limiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (handler instanceof HandlerMethod method && method.hasMethodAnnotation(RateLimited.class)) {
            limiter.consume(clientIpResolver.resolve(request));
        }
        return true;
    }
}
