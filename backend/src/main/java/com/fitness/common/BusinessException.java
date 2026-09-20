package com.fitness.common;

public class BusinessException extends RuntimeException {
    private final ApiErrorCode errorCode;

    public BusinessException(ApiErrorCode errorCode, String message) {
        super(message == null || message.isBlank() ? errorCode.defaultMessage() : message);
        this.errorCode = errorCode;
    }

    public ApiErrorCode getErrorCode() { return errorCode; }
}
