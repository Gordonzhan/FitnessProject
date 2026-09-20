package com.fitness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** 服务端解密微信运动开放数据；不记录密文、IV 或 sessionKey。 */
@Component
public class WeRunDataDecryptor {
    private final ObjectMapper objectMapper;

    @Value("${fitness.wechat.app-id:}")
    private String appId;

    public WeRunDataDecryptor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public WeRunPayload decrypt(String sessionKey, String encryptedData, String iv) {
        if (!StringUtils.hasText(sessionKey) || !StringUtils.hasText(encryptedData) || !StringUtils.hasText(iv)) {
            throw new IllegalArgumentException("微信运动授权数据不完整，请重新同步");
        }
        try {
            byte[] keyBytes = Base64.getDecoder().decode(sessionKey);
            byte[] ivBytes = Base64.getDecoder().decode(iv);
            if (keyBytes.length != 16 || ivBytes.length != 16) {
                throw new PayloadValidationException("微信运动授权数据格式不正确");
            }
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(keyBytes, "AES"), new IvParameterSpec(ivBytes));
            String json = new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)), StandardCharsets.UTF_8);
            JsonNode root = objectMapper.readTree(json);
            return parsePayload(root);
        } catch (SecurityException error) {
            throw error;
        } catch (PayloadValidationException error) {
            throw new IllegalArgumentException(error.getMessage());
        } catch (Exception error) {
            throw new IllegalArgumentException("微信运动数据解密失败，请重新授权后再试");
        }
    }

    /** 解析云托管 getopendata 已换取的明文包；仍校验 watermark AppID。 */
    public WeRunPayload parseCloudData(JsonNode envelope) {
        if (envelope == null || envelope.isMissingNode() || envelope.isNull()) {
            throw new IllegalArgumentException("微信运动开放数据为空，请重新同步");
        }
        JsonNode data = envelope.has("data") ? envelope.path("data") : envelope;
        try {
            return parsePayload(data);
        } catch (PayloadValidationException error) {
            throw new IllegalArgumentException(error.getMessage());
        }
    }

    private WeRunPayload parsePayload(JsonNode root) {
        JsonNode watermark = root.path("watermark");
        String payloadAppId = watermark.path("appid").asText("");
        if (!StringUtils.hasText(appId) || !appId.equals(payloadAppId)) {
            throw new SecurityException("微信运动数据不属于当前小程序");
        }
        JsonNode rows = root.path("stepInfoList");
        if (!rows.isArray()) throw new PayloadValidationException("微信运动数据缺少步数列表");
        List<StepEntry> entries = new ArrayList<>();
        for (JsonNode row : rows) {
            if (!row.has("timestamp") || !row.has("step")) {
                throw new PayloadValidationException("微信运动步数记录格式不正确");
            }
            entries.add(new StepEntry(row.path("timestamp").asLong(), row.path("step").asInt(-1)));
        }
        return new WeRunPayload(entries, watermark.path("timestamp").asLong());
    }

    public record StepEntry(long timestamp, int steps) {}
    public record WeRunPayload(List<StepEntry> stepInfoList, long watermarkTimestamp) {}

    private static class PayloadValidationException extends RuntimeException {
        PayloadValidationException(String message) { super(message); }
    }
}
