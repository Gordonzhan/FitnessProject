package com.fitness.common;

import org.springframework.http.HttpStatus;

public enum ApiErrorCode {
    INVALID_ARGUMENT(HttpStatus.BAD_REQUEST, "请求参数不正确"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "登录已失效，请重新登录"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "无权执行此操作"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "数据不存在"),
    CONFLICT(HttpStatus.CONFLICT, "数据状态冲突"),
    PAYLOAD_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "上传内容超过限制"),
    TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "请求过于频繁"),
    UPSTREAM_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "外部服务暂时不可用"),
    DATABASE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "基础数据暂时无法访问"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "服务器处理失败");

    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() { return status; }
    public int code() { return status.value(); }
    public String defaultMessage() { return defaultMessage; }
}
