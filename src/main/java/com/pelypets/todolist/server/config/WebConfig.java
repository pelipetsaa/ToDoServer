package com.pelypets.todolist.server.config;

import com.pelypets.todolist.server.adapter.in.web.ratelimit.AccountRateLimiter;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.AuthRateLimiter;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.ClientIpResolver;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.RateLimitInterceptor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Rate limits live in this instance's memory: with several instances each enforces them on its own share of the
 * traffic, so the effective limit grows with the instance count.
 */
@Configuration(proxyBeanMethods = false)
class WebConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    WebConfig(AppProperties properties) {
        this.properties = properties;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(
                new AuthRateLimiter(properties.rateLimit().authRequestsPerMinute()),
                new ClientIpResolver(properties.trustProxy(), properties.trustedProxyHops())));
    }

    @Bean
    AccountRateLimiter accountRateLimiter() {
        return new AccountRateLimiter(properties.rateLimit().loginAttemptsPerAccountPerMinute());
    }
}
