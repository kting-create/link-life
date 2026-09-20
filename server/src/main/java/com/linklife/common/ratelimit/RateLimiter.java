package com.linklife.common.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
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
        check(key, LIMIT);
    }

    public void check(String key, int limit) {
        // 防御：limit 误传 0/负数时按 1 处理，避免计数器永远超限全拒绝
        int effective = Math.max(1, limit);
        AtomicInteger count = counters.get(key, k -> new AtomicInteger());
        if (count.incrementAndGet() > effective) {
            throw new BusinessException(ErrorCode.RATE_LIMITED);
        }
    }

    /**
     * 解析真实客户端 IP。生产环境经 nginx 反代，remoteAddr 是 nginx 容器 IP，
     * 需优先取 X-Real-IP / X-Forwarded-For，否则限流 key 会塌缩成全局上限。
     */
    public static String clientIp(HttpServletRequest request) {
        return resolveClientIp(
                request.getHeader("X-Real-IP"),
                request.getHeader("X-Forwarded-For"),
                request.getRemoteAddr());
    }

    static String resolveClientIp(String xRealIp, String xForwardedFor, String remoteAddr) {
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            String firstHop = xForwardedFor.split(",")[0].trim();
            if (!firstHop.isEmpty()) {
                return firstHop;
            }
        }
        return remoteAddr;
    }
}
