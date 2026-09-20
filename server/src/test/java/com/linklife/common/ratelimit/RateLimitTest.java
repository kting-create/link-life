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
    void customLimitOverloadRespectsGivenLimit() {
        RateLimiter limiter = new RateLimiter();
        // 限额 3:第 1~3 次放行,第 4 次拒绝
        for (int i = 0; i < 3; i++) {
            assertDoesNotThrow(() -> limiter.check("u1:gen", 3));
        }
        assertThrows(BusinessException.class, () -> limiter.check("u1:gen", 3));
        // 默认 check(key) 仍是 LIMIT=5 语义
        RateLimiter defaultLimiter = new RateLimiter();
        for (int i = 0; i < 5; i++) {
            assertDoesNotThrow(() -> defaultLimiter.check("u2:gen"));
        }
        assertThrows(BusinessException.class, () -> defaultLimiter.check("u2:gen"));
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
