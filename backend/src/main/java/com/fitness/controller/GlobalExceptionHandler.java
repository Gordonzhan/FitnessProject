package com.fitness.controller;

import com.aliyun.oss.OSSException;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.common.Response;
import com.fitness.service.ImageService;
import com.fitness.service.OssStsService;
import com.fitness.util.OSSUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.format.DateTimeParseException;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Response> handleBusinessException(BusinessException error, HttpServletRequest request) {
        logClientError(request, error.getErrorCode(), error.getMessage());
        return response(error.getErrorCode(), error.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Response> handleBodyValidation(MethodArgumentNotValidException error,
                                                          HttpServletRequest request) {
        String message = error.getBindingResult().getFieldErrors().stream()
                .findFirst().map(item -> item.getDefaultMessage()).orElse("请求参数不正确");
        logClientError(request, ApiErrorCode.INVALID_ARGUMENT, message);
        return response(ApiErrorCode.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler({ConstraintViolationException.class, HandlerMethodValidationException.class,
            BindException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class})
    public ResponseEntity<Response> handleParameterValidation(Exception error, HttpServletRequest request) {
        String message = validationMessage(error);
        logClientError(request, ApiErrorCode.INVALID_ARGUMENT, message);
        return response(ApiErrorCode.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Response> handleUnreadableBody(HttpMessageNotReadableException error,
                                                          HttpServletRequest request) {
        String message = "请求内容格式不正确，请检查日期和数字格式";
        logClientError(request, ApiErrorCode.INVALID_ARGUMENT, message);
        return response(ApiErrorCode.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Response> handleTypeMismatch(MethodArgumentTypeMismatchException error,
                                                        HttpServletRequest request) {
        String message = "请求参数类型不正确";
        logClientError(request, ApiErrorCode.INVALID_ARGUMENT, message);
        return response(ApiErrorCode.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<Response> handleInvalidDate(DateTimeParseException error,
                                                       HttpServletRequest request) {
        String message = "日期格式不正确，请使用真实的 yyyy-MM-dd 日期";
        logClientError(request, ApiErrorCode.INVALID_ARGUMENT, message);
        return response(ApiErrorCode.INVALID_ARGUMENT, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Response> handleMaxUploadSizeExceededException(
            MaxUploadSizeExceededException error, HttpServletRequest request) {
        String message = "图片大小超过上传限制，请选择不超过20MB的图片";
        logClientError(request, ApiErrorCode.PAYLOAD_TOO_LARGE, message);
        return response(ApiErrorCode.PAYLOAD_TOO_LARGE, message);
    }

    @ExceptionHandler(OSSUtil.UploadBusyException.class)
    public ResponseEntity<Response> handleUploadBusy(OSSUtil.UploadBusyException error,
                                                      HttpServletRequest request) {
        logClientError(request, ApiErrorCode.TOO_MANY_REQUESTS, error.getMessage());
        return response(ApiErrorCode.TOO_MANY_REQUESTS, error.getMessage());
    }

    @ExceptionHandler(OssStsService.AuthorizationException.class)
    public ResponseEntity<Response> handleOssAuthorization(OssStsService.AuthorizationException error,
                                                            HttpServletRequest request) {
        log.warn("OSS_UPLOAD_AUTHORIZATION_FAILED requestId={} path={} reason={}",
                requestId(request), request.getRequestURI(), error.getCause() == null
                        ? error.getClass().getSimpleName() : error.getCause().getClass().getSimpleName());
        return response(ApiErrorCode.UPSTREAM_UNAVAILABLE, "OSS上传授权暂不可用，请稍后重试");
    }

    @ExceptionHandler(ImageService.ImageInUseException.class)
    public ResponseEntity<Response> handleImageInUse(ImageService.ImageInUseException error,
                                                      HttpServletRequest request) {
        logClientError(request, ApiErrorCode.CONFLICT, error.getMessage());
        return response(ApiErrorCode.CONFLICT, error.getMessage());
    }

    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<Response> handleForbidden(SecurityException error, HttpServletRequest request) {
        logClientError(request, ApiErrorCode.FORBIDDEN, error.getMessage());
        return response(ApiErrorCode.FORBIDDEN, error.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Response> handleIllegalArgument(IllegalArgumentException error,
                                                           HttpServletRequest request) {
        logClientError(request, ApiErrorCode.INVALID_ARGUMENT, error.getMessage());
        return response(ApiErrorCode.INVALID_ARGUMENT, error.getMessage());
    }

    @ExceptionHandler(OSSException.class)
    public ResponseEntity<Response> handleOssException(OSSException error, HttpServletRequest request) {
        String message = switch (error.getErrorCode()) {
            case "UserDisable", "BucketDisable" -> "OSS服务不可用，请检查阿里云欠费或账号状态";
            case "AccessDenied" -> "OSS访问被拒绝，请检查存储权限";
            case "NoSuchBucket" -> "OSS存储空间不存在，请检查Bucket配置";
            default -> "OSS图片操作失败，请稍后重试";
        };
        log.warn("UPSTREAM_OSS_FAILED requestId={} path={} ossCode={} ossRequestId={}",
                requestId(request), request.getRequestURI(), error.getErrorCode(), error.getRequestId());
        return response(ApiErrorCode.UPSTREAM_UNAVAILABLE, message);
    }

    @ExceptionHandler(ImageController.ImageOperationException.class)
    public ResponseEntity<Response> handleImageOperation(ImageController.ImageOperationException error,
                                                          HttpServletRequest request) {
        log.error("IMAGE_SERVICE_FAILED requestId={} path={}",
                requestId(request), request.getRequestURI(), error);
        return response(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                "图片服务暂不可用或请求超时，请检查网络和OSS配置后重试");
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Response> handleDataConflict(DataIntegrityViolationException error,
                                                        HttpServletRequest request) {
        log.warn("DATABASE_CONFLICT requestId={} method={} path={}",
                requestId(request), request.getMethod(), request.getRequestURI());
        return response(ApiErrorCode.CONFLICT, "数据已存在或状态发生变化，请刷新后重试");
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Response> handleDataAccessException(DataAccessException error,
                                                               HttpServletRequest request) {
        log.error("DATABASE_ACCESS_FAILED requestId={} method={} path={}",
                requestId(request), request.getMethod(), request.getRequestURI(), error);
        return response(ApiErrorCode.DATABASE_UNAVAILABLE, "基础数据暂时无法访问，请稍后重试");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Response> handleException(Exception error, HttpServletRequest request) {
        log.error("UNEXPECTED_SERVER_ERROR requestId={} method={} path={}",
                requestId(request), request.getMethod(), request.getRequestURI(), error);
        return response(ApiErrorCode.INTERNAL_ERROR, "服务器处理失败，请稍后重试");
    }

    private ResponseEntity<Response> response(ApiErrorCode errorCode, String message) {
        return ResponseEntity.status(errorCode.status()).body(Response.error(errorCode.code(), message));
    }

    private void logClientError(HttpServletRequest request, ApiErrorCode errorCode, String message) {
        log.info("CLIENT_REQUEST_REJECTED requestId={} method={} path={} code={} reason={}",
                requestId(request), request.getMethod(), request.getRequestURI(), errorCode.code(), message);
    }

    private String validationMessage(Exception error) {
        if (error instanceof ConstraintViolationException violation) {
            return violation.getConstraintViolations().stream().findFirst()
                    .map(item -> item.getMessage()).orElse("请求参数不正确");
        }
        if (error instanceof HandlerMethodValidationException validation) {
            return validation.getAllErrors().stream().findFirst()
                    .map(item -> item.getDefaultMessage()).orElse("请求参数不正确");
        }
        if (error instanceof BindException bind) {
            return bind.getFieldErrors().stream().findFirst()
                    .map(item -> item.getDefaultMessage()).orElse("请求参数不正确");
        }
        if (error instanceof MissingServletRequestPartException) return "请选择要上传的图片";
        return "缺少必要的请求参数";
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unavailable" : value.toString();
    }
}
