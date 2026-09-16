package com.linklife.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    CIRCLE_NOT_FOUND(1001, "圈子不存在", HttpStatus.BAD_REQUEST),
    NOT_CIRCLE_MEMBER(1002, "你不是该圈子成员", HttpStatus.FORBIDDEN),
    INVITE_CODE_INVALID(1003, "邀请码无效", HttpStatus.BAD_REQUEST),
    ALREADY_MEMBER(1004, "已是圈子成员", HttpStatus.BAD_REQUEST),
    BINDING_CODE_INVALID(1005, "绑定码无效或已过期", HttpStatus.BAD_REQUEST),
    WX_LOGIN_FAILED(2001, "微信登录失败", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(2002, "登录状态无效", HttpStatus.UNAUTHORIZED);

    public final int code;
    public final String message;
    public final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
