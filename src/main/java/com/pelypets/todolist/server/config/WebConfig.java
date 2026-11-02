package com.pelypets.todolist.server.config;

import com.pelypets.todolist.server.adapter.in.web.ratelimit.AuthRateLimiter;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.ClientIpResolver;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    WebConfig(AppProperties properties) {
        this.rateLimitInterceptor = new RateLimitInterceptor(
                new AuthRateLimiter(properties.rateLimit().authRequestsPerMinute()),
                new ClientIpResolver(properties.trustProxy()));
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor);
    }
}
