package com.pelypets.todolist.server.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pelypets.todolist.server.adapter.in.web.ratelimit.AuthRateLimiter;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.ClientIpResolver;
import com.pelypets.todolist.server.adapter.in.web.ratelimit.RateLimitExceededException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class RateLimitingTest {

    @Test
    void eachClientHasItsOwnBudget() {
        var limiter = new AuthRateLimiter(2);
        limiter.consume("a");
        limiter.consume("a");

        assertThatThrownBy(() -> limiter.consume("a"))
                .isInstanceOfSatisfying(RateLimitExceededException.class, e -> assertThat(e.retryAfterSeconds()).isBetween(1L, 60L));
        limiter.consume("b");
    }

    @Test
    void forwardedForIsOnlyTrustedBehindAProxyAndOnlyItsLastHop() {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr("10.0.0.1");
        request.addHeader("X-Forwarded-For", "6.6.6.6, 203.0.113.7");

        assertThat(new ClientIpResolver(false).resolve(request)).isEqualTo("10.0.0.1");
        assertThat(new ClientIpResolver(true).resolve(request)).isEqualTo("203.0.113.7");
    }
}
