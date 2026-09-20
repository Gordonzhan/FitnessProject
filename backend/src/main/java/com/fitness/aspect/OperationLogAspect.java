package com.fitness.aspect;

import com.aliyun.oss.OSSException;
import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.common.Response;
import com.fitness.controller.ImageController;
import com.fitness.controller.RequestTraceFilter;
import com.fitness.entity.OperationLog;
import com.fitness.service.ImageService;
import com.fitness.service.OperationLogService;
import com.fitness.util.OSSUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.HexFormat;

@Aspect
@Component
public class OperationLogAspect {
    private static final Logger logger = LoggerFactory.getLogger(OperationLogAspect.class);

    @Autowired
    private OperationLogService operationLogService;

    @Value("${fitness.performance.slow-request-ms:800}")
    private long slowRequestMs = 800;

    @Pointcut("@within(org.springframework.web.bind.annotation.RestController)")
    public void controllerPointcut() {
    }

    @Around("controllerPointcut()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();
        Throwable failure = null;
        Object result = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable error) {
            failure = error;
            throw error;
        } finally {
            long executionTime = System.currentTimeMillis() - startTime;
            logSlowRequest(executionTime);
            AuditedOperation auditedOperation = auditedOperation(joinPoint);
            if (auditedOperation != null || isPermissionDenied(failure)) {
                try {
                    recordAudit(joinPoint, auditedOperation, executionTime, failure, result);
                } catch (Exception logError) {
                    // 审计使用独立事务：失败不覆盖原业务异常，也不把已提交业务误报为失败。
                    logger.warn("AUDIT_LOG_WRITE_FAILED method={} reason={}",
                            joinPoint.getSignature().toShortString(), logError.getClass().getSimpleName());
                }
            }
        }
    }

    private void logSlowRequest(long executionTime) {
        if (executionTime < Math.max(slowRequestMs, 0)) return;
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        HttpServletRequest request = attributes.getRequest();
        logger.warn("SLOW_REQUEST requestId={} method={} path={} elapsedMs={}",
                requestId(request), request.getMethod(), request.getRequestURI(), executionTime);
    }

    private void recordAudit(ProceedingJoinPoint joinPoint, AuditedOperation annotation,
                             long executionTime, Throwable failure, Object result) {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) return;
        HttpServletRequest request = attributes.getRequest();
        int statusCode = statusCode(result, failure);
        boolean denied = isPermissionDenied(failure);

        OperationLog operationLog = new OperationLog();
        operationLog.setRequestId(requestId(request));
        Object userId = request.getAttribute(AuthContext.USER_ID_ATTRIBUTE);
        if (userId instanceof Number number) operationLog.setUserId(number.longValue());
        operationLog.setEventType((denied ? AuditEvent.PERMISSION_DENIED : annotation.value()).name());
        operationLog.setTargetRef(targetRef(joinPoint, annotation));
        operationLog.setResult(failure == null && statusCode == 200 ? "SUCCESS" : "FAILURE");
        operationLog.setIp(request.getRemoteAddr());
        operationLog.setInterfaceName(request.getRequestURI());
        operationLog.setMethod(request.getMethod());
        // 字段为兼容历史表结构保留；新审计日志不持久化查询词、请求正文或登录凭据。
        operationLog.setRequestParams(null);
        operationLog.setStatusCode(statusCode);
        if (failure != null) operationLog.setErrorMessage(failure.getClass().getSimpleName());
        operationLog.setExecutionTime(executionTime);
        operationLog.setOperationTime(new Date());
        operationLogService.saveLog(operationLog);
    }

    private AuditedOperation auditedOperation(ProceedingJoinPoint joinPoint) {
        if (!(joinPoint.getSignature() instanceof MethodSignature signature)) return null;
        return signature.getMethod().getAnnotation(AuditedOperation.class);
    }

    private boolean isPermissionDenied(Throwable failure) {
        return failure instanceof SecurityException
                || failure instanceof BusinessException business
                && business.getErrorCode() == ApiErrorCode.FORBIDDEN;
    }

    private String targetRef(ProceedingJoinPoint joinPoint, AuditedOperation annotation) {
        if (annotation == null || annotation.targetArgument() < 0
                || annotation.targetArgument() >= joinPoint.getArgs().length) return null;
        Object target = joinPoint.getArgs()[annotation.targetArgument()];
        if (target == null) return null;
        if (!annotation.targetProperty().isBlank()) {
            try {
                Method accessor = target.getClass().getMethod(annotation.targetProperty());
                target = accessor.invoke(target);
            } catch (ReflectiveOperationException error) {
                logger.warn("AUDIT_TARGET_EXTRACTION_FAILED event={} property={} reason={}",
                        annotation.value(), annotation.targetProperty(), error.getClass().getSimpleName());
                return null;
            }
        }
        if (target == null) return null;
        String value = target.toString();
        if (annotation.value() == AuditEvent.OSS_IMAGE_DELETE) return sha256(value);
        return value.matches("[A-Za-z0-9_-]{1,100}") ? value : null;
    }

    private String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            return null;
        }
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? null : value.toString();
    }

    private int statusCode(Object result, Throwable failure) {
        if (result instanceof Response response) return response.getCode();
        if (failure instanceof BusinessException business) return business.getErrorCode().code();
        if (failure instanceof IllegalArgumentException || failure instanceof DateTimeParseException) {
            return ApiErrorCode.INVALID_ARGUMENT.code();
        }
        if (failure instanceof SecurityException) return ApiErrorCode.FORBIDDEN.code();
        if (failure instanceof DataIntegrityViolationException
                || failure instanceof ImageService.ImageInUseException) return ApiErrorCode.CONFLICT.code();
        if (failure instanceof MaxUploadSizeExceededException) return ApiErrorCode.PAYLOAD_TOO_LARGE.code();
        if (failure instanceof OSSUtil.UploadBusyException) return ApiErrorCode.TOO_MANY_REQUESTS.code();
        if (failure instanceof OSSException || failure instanceof ImageController.ImageOperationException) {
            return ApiErrorCode.UPSTREAM_UNAVAILABLE.code();
        }
        if (failure instanceof DataAccessException) return ApiErrorCode.DATABASE_UNAVAILABLE.code();
        return failure == null ? 200 : ApiErrorCode.INTERNAL_ERROR.code();
    }
}
