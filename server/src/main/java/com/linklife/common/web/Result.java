package com.linklife.common.web;

import com.linklife.common.exception.ErrorCode;

public record Result<T>(int code, String message, T data) {
    public static <T> Result<T> ok(T data) {
        return new Result<>(0, "ok", data);
    }
    public static Result<Void> ok() {
        return ok(null);
    }
    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }
    public static Result<Void> error(ErrorCode errorCode) {
        return new Result<>(errorCode.code, errorCode.message, null);
    }
}
