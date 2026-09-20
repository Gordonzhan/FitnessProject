package com.fitness.controller;

import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.dto.ImageDeleteRequest;
import com.fitness.dto.ImageUploadConfirmRequest;
import com.fitness.dto.ImageUploadTicketRequest;
import com.fitness.dto.ImageUploadTicketResponse;
import com.fitness.service.ImageService;
import com.fitness.service.OssDirectUploadService;
import com.fitness.util.OSSUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/image")
public class ImageController {
    private static final Logger log = LoggerFactory.getLogger(ImageController.class);
    private static final Pattern SAFE_ATTEMPT_ID = Pattern.compile("[A-Za-z0-9_-]{8,64}");
    private final OSSUtil ossUtil;
    private final ImageService imageService;
    private final OssDirectUploadService directUploadService;

    public ImageController(OSSUtil ossUtil, ImageService imageService,
                           OssDirectUploadService directUploadService) {
        this.ossUtil = ossUtil;
        this.imageService = imageService;
        this.directUploadService = directUploadService;
    }

    @PostMapping("/upload")
    public Response uploadImage(@RequestParam("file") MultipartFile file, HttpServletRequest request) {
        long startedAt = System.nanoTime();
        String requestId = requestId(request);
        String attemptId = safeAttemptId(request.getHeader("X-Upload-Attempt-Id"));
        log.info("IMAGE_UPLOAD_LEGACY_STARTED requestId={} uploadAttemptId={} sizeBytes={}", requestId, attemptId,
                file == null ? 0 : file.getSize());
        try {
            String imageUrl = ossUtil.uploadImage(file, AuthContext.getUserId(request));
            log.info("IMAGE_UPLOAD_LEGACY_SUCCEEDED requestId={} uploadAttemptId={} elapsedMs={}",
                    requestId, attemptId, elapsedMillis(startedAt));
            return Response.success(Map.of("imageUrl", imageUrl));
        } catch (Exception e) {
            log.warn("IMAGE_UPLOAD_LEGACY_FAILED requestId={} uploadAttemptId={} elapsedMs={} reason={}",
                    requestId, attemptId, elapsedMillis(startedAt), e.getClass().getSimpleName());
            throw propagate(e);
        }
    }

    @PostMapping("/upload-ticket")
    public Response issueUploadTicket(@Valid @RequestBody ImageUploadTicketRequest body,
                                      HttpServletRequest request) {
        long startedAt = System.nanoTime();
        String requestId = requestId(request);
        String attemptId = safeAttemptId(body.uploadAttemptId());
        log.info("IMAGE_UPLOAD_TICKET_STARTED requestId={} uploadAttemptId={} sizeBytes={} format={}",
                requestId, attemptId, body.fileSize(), body.format().toLowerCase());
        try {
            ImageUploadTicketResponse ticket = directUploadService.issue(
                    AuthContext.getUserId(request), body.fileSize(), body.format());
            log.info("IMAGE_UPLOAD_TICKET_SUCCEEDED requestId={} uploadAttemptId={} elapsedMs={}",
                    requestId, attemptId, elapsedMillis(startedAt));
            return Response.success(ticket);
        } catch (RuntimeException error) {
            log.warn("IMAGE_UPLOAD_TICKET_FAILED requestId={} uploadAttemptId={} elapsedMs={} reason={}",
                    requestId, attemptId, elapsedMillis(startedAt), error.getClass().getSimpleName());
            throw error;
        }
    }

    @PostMapping("/upload-confirm")
    public Response confirmUpload(@Valid @RequestBody ImageUploadConfirmRequest body,
                                  HttpServletRequest request) {
        long startedAt = System.nanoTime();
        String requestId = requestId(request);
        String attemptId = safeAttemptId(body.uploadAttemptId());
        log.info("IMAGE_UPLOAD_CONFIRM_STARTED requestId={} uploadAttemptId={}", requestId, attemptId);
        try {
            String imageUrl = directUploadService.confirm(AuthContext.getUserId(request), body.ticketId());
            log.info("IMAGE_UPLOAD_CONFIRM_SUCCEEDED requestId={} uploadAttemptId={} elapsedMs={}",
                    requestId, attemptId, elapsedMillis(startedAt));
            return Response.success(Map.of("imageUrl", imageUrl));
        } catch (RuntimeException error) {
            log.warn("IMAGE_UPLOAD_CONFIRM_FAILED requestId={} uploadAttemptId={} elapsedMs={} reason={}",
                    requestId, attemptId, elapsedMillis(startedAt), error.getClass().getSimpleName());
            throw error;
        }
    }

    private long elapsedMillis(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000L;
    }

    private String requestId(HttpServletRequest request) {
        Object value = request.getAttribute(RequestTraceFilter.REQUEST_ID_ATTRIBUTE);
        return value == null ? "unavailable" : value.toString();
    }

    private String safeAttemptId(String value) {
        return value != null && SAFE_ATTEMPT_ID.matcher(value).matches() ? value : "unavailable";
    }

    // 统一为 JSON 请求和 {code,message,data} 响应。仅清理当前用户未保存的图片。
    @PostMapping("/delete")
    @AuditedOperation(value = AuditEvent.OSS_IMAGE_DELETE,
            targetArgument = 0, targetProperty = "imageUrl")
    public Response deleteImage(@Valid @RequestBody ImageDeleteRequest body, HttpServletRequest request) {
        imageService.deleteUnusedUpload(AuthContext.getUserId(request), body.imageUrl());
        return Response.success();
    }

    private RuntimeException propagate(Exception error) {
        if (error instanceof RuntimeException runtime) return runtime;
        return new ImageOperationException(error);
    }

    public static class ImageOperationException extends RuntimeException {
        public ImageOperationException(Throwable cause) { super(cause); }
    }
}
