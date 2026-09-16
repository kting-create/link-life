package com.linklife.common.security;

import com.linklife.common.exception.BusinessException;
import com.linklife.common.exception.ErrorCode;

public final class UserContext {
    private static final ThreadLocal<Long> CURRENT = new ThreadLocal<>();

    private UserContext() {
    }

    public static void set(Long userId) {
        CURRENT.set(userId);
    }

    public static long requireUserId() {
        Long id = CURRENT.get();
        if (id == null) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }
        return id;
    }

    public static void clear() {
        CURRENT.remove();
    }
}
