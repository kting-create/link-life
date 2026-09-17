package com.linklife.common.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.linklife.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

class RateLimitTest {

    @Test
    void sixthCallWithinWindowIsRejected() {
        RateLimiter limiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> limiter.check("user1:bind"));
        }
        assertThrows(BusinessException.class, () -> limiter.check("user1:bind"));
        // 不同 key 互不影响
        assertDoesNotThrow(() -> limiter.check("user2:bind"));
    }
}
