package com.linklife.common.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void clientIpPrefersForwardedHeadersOverRemoteAddr() {
        // 优先 X-Real-IP
        assertEquals("1.2.3.4",
                RateLimiter.resolveClientIp("1.2.3.4", "5.6.7.8, 10.0.0.1", "172.17.0.9"));
        // 无 X-Real-IP 时取 X-Forwarded-For 第一个跳
        assertEquals("5.6.7.8",
                RateLimiter.resolveClientIp(null, "5.6.7.8, 10.0.0.1", "172.17.0.9"));
        // 多跳带空格也正确 trim
        assertEquals("5.6.7.8",
                RateLimiter.resolveClientIp(null, " 5.6.7.8 , 10.0.0.1", "172.17.0.9"));
        // 两个头都缺失时回退 remoteAddr
        assertEquals("172.17.0.9",
                RateLimiter.resolveClientIp(null, null, "172.17.0.9"));
        // 空白头视同缺失
        assertEquals("172.17.0.9",
                RateLimiter.resolveClientIp("", " ", "172.17.0.9"));
    }
}
