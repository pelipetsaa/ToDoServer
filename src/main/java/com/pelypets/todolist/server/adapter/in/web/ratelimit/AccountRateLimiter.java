package com.pelypets.todolist.server.adapter.in.web.ratelimit;

import com.pelypets.todolist.server.domain.model.DomainRules;

/**
 * Limits password sign-in attempts per account, whatever addresses they come from, so guessing one account's password
 * from many IPs is as slow as from one. A locked-out owner waits at most a minute.
 */
public class AccountRateLimiter {

    private final AuthRateLimiter buckets;

    public AccountRateLimiter(int attemptsPerMinute) {
        this.buckets = new AuthRateLimiter(attemptsPerMinute);
    }

    /** @throws RateLimitExceededException when the account has used up its budget */
    public void consume(String email) {
        buckets.consume(DomainRules.normalizeEmail(email));
    }
}
