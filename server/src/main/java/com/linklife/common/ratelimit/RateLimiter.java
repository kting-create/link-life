package com.linklife.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

@Component
public class RateLimiter {

    private static final int LIMIT = 5;

    private final Cache<String, AtomicInteger> counters = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(1))
            .build();

    public void check(String key) {
        AtomicInteger count = counters.get(key, k -> new AtomicInteger());
        if (count.incrementAndGet() > LIMIT) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }
}
