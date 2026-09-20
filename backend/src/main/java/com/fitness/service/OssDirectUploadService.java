package com.fitness.service;

import com.aliyun.oss.OSSException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.ImageUploadTicketResponse;
import com.fitness.entity.ImageUploadTicket;
import com.fitness.mapper.ImageUploadTicketMapper;
import com.fitness.util.OSSUtil;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class OssDirectUploadService {
    private static final Logger log = LoggerFactory.getLogger(OssDirectUploadService.class);
    static final long MAX_IMAGE_BYTES = 20L * 1024 * 1024;
    private static final String SIGNATURE_VERSION = "OSS4-HMAC-SHA256";
    private static final DateTimeFormatter OSS_DATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final DateTimeFormatter DATE_SCOPE = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC);

    private final ImageUploadTicketMapper mapper;
    private final OssStsService stsService;
    private final OSSUtil ossUtil;
    private final ObjectMapper objectMapper;
    private final String region;
    private final long ticketTtlSeconds;

    public OssDirectUploadService(
            ImageUploadTicketMapper mapper,
            OssStsService stsService,
            OSSUtil ossUtil,
            ObjectMapper objectMapper,
            @Value("${fitness.oss.region:cn-beijing}") String region,
            @Value("${fitness.oss.upload-ticket-ttl-seconds:300}") long ticketTtlSeconds) {
        this.mapper = mapper;
        this.stsService = stsService;
        this.ossUtil = ossUtil;
        this.objectMapper = objectMapper;
        this.region = region;
        if (ticketTtlSeconds < 60 || ticketTtlSeconds > 600) {
            throw new IllegalArgumentException("OSS上传授权有效期必须在60至600秒之间");
        }
        this.ticketTtlSeconds = ticketTtlSeconds;
    }

    public ImageUploadTicketResponse issue(Long userId, long fileSize, String requestedFormat) {
        if (userId == null || userId <= 0) throw new SecurityException("登录用户无效");
        if (fileSize < 1 || fileSize > MAX_IMAGE_BYTES) {
            throw new IllegalArgumentException(fileSize < 1 ? "图片不能为空" : "单张图片不能超过20MB");
        }
        String format = normalizeFormat(requestedFormat);
        String mime = "image/" + format;
        String ticketId = UUID.randomUUID().toString();
        String objectKey = ossUtil.imageKey(userId, format);
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plusSeconds(ticketTtlSeconds);

        OssStsService.TemporaryCredentials credentials = stsService.assumeForObject(objectKey, ticketId);
        Map<String, String> fields = signedFields(
                credentials, objectKey, mime, fileSize, issuedAt, expiresAt);

        ImageUploadTicket ticket = new ImageUploadTicket();
        ticket.setTicketId(ticketId);
        ticket.setUserId(userId);
        ticket.setObjectKey(objectKey);
        ticket.setExpectedSize(fileSize);
        ticket.setExpectedFormat(format);
        ticket.setExpectedMime(mime);
        ticket.setStatus("ISSUED");
        ticket.setExpiresAt(LocalDateTime.ofInstant(expiresAt, ZoneOffset.UTC));
        ticket.setCreatedAt(LocalDateTime.ofInstant(issuedAt, ZoneOffset.UTC));
        mapper.insert(ticket);

        return new ImageUploadTicketResponse(
                ticketId, ossUtil.uploadHost(), objectKey, expiresAt, Collections.unmodifiableMap(fields));
    }

    public String confirm(Long userId, String ticketId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (mapper.claim(ticketId, userId, now) != 1) {
            throw ticketStateError(userId, ticketId, now);
        }

        ImageUploadTicket ticket = mapper.findById(ticketId);
        if (ticket == null) {
            throw new BusinessException(ApiErrorCode.CONFLICT, "上传授权状态已变化，请重新申请");
        }
        try {
            OSSUtil.RemoteImage remote = ossUtil.inspectImageObject(ticket.getObjectKey());
            if (remote.size() != ticket.getExpectedSize()) {
                throw new BusinessException(ApiErrorCode.INVALID_ARGUMENT, "OSS图片大小与上传授权不一致");
            }
            String detected = OSSUtil.detectFormat(remote.header());
            if (!detected.equals(ticket.getExpectedFormat())) {
                throw new BusinessException(ApiErrorCode.INVALID_ARGUMENT, "图片实际格式与上传授权不一致");
            }
            if (!ticket.getExpectedMime().equalsIgnoreCase(String.valueOf(remote.contentType()))) {
                throw new BusinessException(ApiErrorCode.INVALID_ARGUMENT, "OSS图片Content-Type与上传授权不一致");
            }
            if (mapper.markConfirmed(ticketId, now) != 1) {
                throw new BusinessException(ApiErrorCode.CONFLICT, "上传授权状态已变化，请重新上传");
            }
            return ossUtil.imageUrlForKey(ticket.getObjectKey());
        } catch (RuntimeException error) {
            safeDelete(ticket.getObjectKey());
            safeMarkFailed(ticketId);
            if (error instanceof OSSException ossError && "NoSuchKey".equals(ossError.getErrorCode())) {
                throw new BusinessException(ApiErrorCode.NOT_FOUND, "OSS中未找到待确认图片，请重新上传");
            }
            if (error instanceof IllegalArgumentException) {
                throw new BusinessException(ApiErrorCode.INVALID_ARGUMENT, "图片文件头校验失败，请重新选择图片");
            }
            throw error;
        }
    }

    /** 清理从未确认的过期对象；先原子失效 ticket，再按服务端保存的 key 删除。 */
    public int cleanupExpiredTickets(int limit) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        int cleaned = 0;
        for (ImageUploadTicket ticket : mapper.findExpiredIssued(now, Math.max(1, Math.min(limit, 500)))) {
            if (mapper.markExpired(ticket.getTicketId(), ticket.getUserId()) == 1) {
                safeDelete(ticket.getObjectKey());
                cleaned++;
            }
        }
        return cleaned;
    }

    private RuntimeException ticketStateError(Long userId, String ticketId, LocalDateTime now) {
        ImageUploadTicket ticket = mapper.findById(ticketId);
        if (ticket == null || !Objects.equals(ticket.getUserId(), userId)) {
            return new SecurityException("上传授权不存在或不属于当前用户");
        }
        if ("ISSUED".equals(ticket.getStatus()) && ticket.getExpiresAt().isBefore(now)) {
            if (mapper.markExpired(ticketId, userId) == 1) safeDelete(ticket.getObjectKey());
            return new BusinessException(ApiErrorCode.CONFLICT, "上传授权已过期，请重新申请");
        }
        return new BusinessException(ApiErrorCode.CONFLICT,
                "CONFIRMED".equals(ticket.getStatus()) ? "上传授权已确认，不能重复使用" : "上传授权不可重复使用");
    }

    private void safeDelete(String objectKey) {
        try {
            ossUtil.deleteObjectKey(objectKey);
        } catch (RuntimeException ignored) {
            // 确认失败的主错误优先返回，且绝不记录 key。
            log.warn("IMAGE_UPLOAD_OBJECT_CLEANUP_PENDING reason={}", ignored.getClass().getSimpleName());
        }
    }

    private void safeMarkFailed(String ticketId) {
        try {
            mapper.markFailed(ticketId);
        } catch (RuntimeException ignored) {
            log.warn("IMAGE_UPLOAD_TICKET_FAILURE_STATUS_PENDING reason={}", ignored.getClass().getSimpleName());
        }
    }

    private Map<String, String> signedFields(OssStsService.TemporaryCredentials credentials,
                                              String objectKey, String mime, long fileSize,
                                              Instant issuedAt, Instant expiresAt) {
        String date = DATE_SCOPE.format(issuedAt);
        String requestDate = OSS_DATE.format(issuedAt);
        String credential = credentials.accessKeyId() + "/" + date + "/" + region
                + "/oss/aliyun_v4_request";
        List<Object> conditions = new ArrayList<>();
        conditions.add(Map.of("bucket", ossUtil.bucketName()));
        conditions.add(Map.of("x-oss-signature-version", SIGNATURE_VERSION));
        conditions.add(Map.of("x-oss-credential", credential));
        conditions.add(Map.of("x-oss-security-token", credentials.securityToken()));
        conditions.add(Map.of("x-oss-date", requestDate));
        conditions.add(List.of("eq", "$key", objectKey));
        conditions.add(List.of("eq", "$content-type", mime));
        // 绑定客户端声明的精确大小；同时该大小已被限制在业务的 1～20MB 内。
        conditions.add(List.of("content-length-range", fileSize, fileSize));
        conditions.add(List.of("eq", "$success_action_status", "204"));
        conditions.add(List.of("eq", "$x-oss-forbid-overwrite", "true"));

        try {
            String policyJson = objectMapper.writeValueAsString(Map.of(
                    "expiration", expiresAt.toString(), "conditions", conditions));
            String policy = Base64.getEncoder().encodeToString(policyJson.getBytes(StandardCharsets.UTF_8));
            String signature = signV4(credentials.accessKeySecret(), date, region, policy);
            LinkedHashMap<String, String> fields = new LinkedHashMap<>();
            fields.put("key", objectKey);
            fields.put("content-type", mime);
            fields.put("success_action_status", "204");
            fields.put("x-oss-forbid-overwrite", "true");
            fields.put("x-oss-signature-version", SIGNATURE_VERSION);
            fields.put("x-oss-credential", credential);
            fields.put("x-oss-security-token", credentials.securityToken());
            fields.put("x-oss-date", requestDate);
            fields.put("policy", policy);
            fields.put("x-oss-signature", signature);
            return fields;
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("生成OSS上传Policy失败", error);
        }
    }

    static String signV4(String secret, String date, String region, String encodedPolicy) {
        byte[] dateKey = hmac(("aliyun_v4" + secret).getBytes(StandardCharsets.UTF_8), date);
        byte[] regionKey = hmac(dateKey, region);
        byte[] serviceKey = hmac(regionKey, "oss");
        byte[] signingKey = hmac(serviceKey, "aliyun_v4_request");
        return HexFormat.of().formatHex(hmac(signingKey, encodedPolicy));
    }

    private static byte[] hmac(byte[] key, String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new IllegalStateException("生成OSS V4签名失败", error);
        }
    }

    static String normalizeFormat(String value) {
        if (value == null) throw new IllegalArgumentException("图片格式不能为空");
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> "jpeg";
            case "png" -> "png";
            case "gif" -> "gif";
            case "webp" -> "webp";
            default -> throw new IllegalArgumentException("仅支持 JPG、PNG、GIF、WebP 图片");
        };
    }
}
