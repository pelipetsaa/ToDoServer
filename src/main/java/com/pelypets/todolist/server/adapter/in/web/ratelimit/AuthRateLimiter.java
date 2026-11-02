package com.pelypets.todolist.server.adapter.in.web.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import java.time.Duration;

/**
 * A token bucket per client, refilled in full every minute. Buckets of idle clients are evicted, so memory stays
 * bounded however many addresses call. The state is per instance; with several instances each one enforces the limit.
 */
public class AuthRateLimiter {

    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final int requestsPerMinute;
    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
            .expireAfterAccess(REFILL_PERIOD.multipliedBy(2))
            .maximumSize(100_000)
            .build();

    public AuthRateLimiter(int requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    /** @throws RateLimitExceededException when the client has used up its budget */
    public void consume(String clientKey) {
        ConsumptionProbe probe = buckets.get(clientKey, key -> newBucket()).tryConsumeAndReturnRemaining(1);
        if (!probe.isConsumed()) {
            long seconds = Duration.ofNanos(probe.getNanosToWaitForRefill()).toSeconds() + 1;
            throw new RateLimitExceededException(seconds);
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(requestsPerMinute)
                        .refillIntervally(requestsPerMinute, REFILL_PERIOD)
                        .build())
                .build();
    }
}
