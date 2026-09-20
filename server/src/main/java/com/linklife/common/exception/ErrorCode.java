package com.linklife.common.exception;

import org.springframework.http.HttpStatus;

public enum ErrorCode {
    CIRCLE_NOT_FOUND(1001, "圈子不存在", HttpStatus.BAD_REQUEST),
    NOT_CIRCLE_MEMBER(1002, "你不是该圈子成员", HttpStatus.FORBIDDEN),
    INVITE_CODE_INVALID(1003, "邀请码无效", HttpStatus.BAD_REQUEST),
    ALREADY_MEMBER(1004, "已是圈子成员", HttpStatus.BAD_REQUEST),
    BINDING_CODE_INVALID(1005, "绑定码无效或已过期", HttpStatus.BAD_REQUEST),
    WX_LOGIN_FAILED(2001, "微信登录失败", HttpStatus.UNAUTHORIZED),
    INVALID_TOKEN(2002, "登录状态无效", HttpStatus.UNAUTHORIZED),
    SHEET_NOT_FOUND(3001, "清单不存在", HttpStatus.BAD_REQUEST),
    ITEM_NOT_FOUND(3002, "菜品不存在", HttpStatus.BAD_REQUEST),
    ITEM_STATUS_INVALID(3003, "当前状态不允许该操作", HttpStatus.BAD_REQUEST),
    SHEET_COMPLETED(3004, "清单已收单", HttpStatus.BAD_REQUEST),
    NOT_ITEM_CLAIMANT(3005, "仅认领人可操作", HttpStatus.FORBIDDEN),
    NOTIFICATION_NOT_FOUND(3006, "通知不存在", HttpStatus.BAD_REQUEST),
    RATE_LIMITED(4001, "请求过于频繁", HttpStatus.TOO_MANY_REQUESTS),
    TOKEN_REVOKED(3007, "登录已失效", HttpStatus.UNAUTHORIZED),
    RECIPE_NOT_FOUND(5001, "菜谱不存在", HttpStatus.NOT_FOUND),
    RECIPE_VERSION_LIMIT(5002, "已达版本上限，请先回滚旧版本", HttpStatus.BAD_REQUEST),
    RECIPE_AI_FAILED(5003, "AI 生成失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR),
    RECIPE_PARSE_FAILED(5004, "AI 返回内容解析失败", HttpStatus.INTERNAL_SERVER_ERROR),
    PANTRY_ITEM_EXISTS(5005, "该条目已存在", HttpStatus.BAD_REQUEST),
    RECIPE_ALREADY_EXISTS(5006, "该菜品已有菜谱", HttpStatus.CONFLICT),
    PANTRY_ITEM_NOT_FOUND(5007, "条目不存在", HttpStatus.NOT_FOUND),
    RECIPE_CONTENT_INVALID(5008, "菜谱内容不完整：食材与步骤不能为空", HttpStatus.BAD_REQUEST),
    FILE_TYPE_INVALID(6001, "仅支持 jpg/png/webp 图片", HttpStatus.BAD_REQUEST),
    FILE_TOO_LARGE(6002, "图片不能超过 5MB", HttpStatus.BAD_REQUEST),
    PHOTO_LIMIT_EXCEEDED(6003, "该菜谱照片已达上限", HttpStatus.BAD_REQUEST),
    PHOTO_NOT_FOUND(6004, "照片不存在", HttpStatus.NOT_FOUND),
    VISION_AI_FAILED(6005, "AI 视觉分析失败，请重试", HttpStatus.INTERNAL_SERVER_ERROR),
    NOTHING_TO_APPLY(6006, "无可应用的修改建议", HttpStatus.BAD_REQUEST),
    PHOTO_NO_PERMISSION(6007, "仅上传者或圈主可删除该照片", HttpStatus.FORBIDDEN),
    PHOTO_STEP_INVALID(6008, "步骤不存在", HttpStatus.BAD_REQUEST);

    public final int code;
    public final String message;
    public final HttpStatus httpStatus;

    ErrorCode(int code, String message, HttpStatus httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
