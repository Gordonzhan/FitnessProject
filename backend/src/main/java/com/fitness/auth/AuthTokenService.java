package com.fitness.auth;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

@Service
public class AuthTokenService {
    private static final Logger logger = LoggerFactory.getLogger(AuthTokenService.class);
    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${fitness.auth.token-secret:}")
    private String configuredSecret;

    @Value("${fitness.auth.token-ttl-seconds:604800}")
    private long tokenTtlSeconds;

    private byte[] signingKey;

    @PostConstruct
    void initialize() {
        if (StringUtils.hasText(configuredSecret)) {
            signingKey = configuredSecret.getBytes(StandardCharsets.UTF_8);
            return;
        }

        signingKey = new byte[32];
        new SecureRandom().nextBytes(signingKey);
        logger.warn("FITNESS_AUTH_TOKEN_SECRET 未配置，本次启动使用临时签名密钥；服务重启后小程序会自动重新登录");
    }

    public String createToken(Long userId) {
        long expiresAt = Instant.now().getEpochSecond() + tokenTtlSeconds;
        String payload = userId + ":" + expiresAt;
        String encodedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encodedPayload + "." + sign(encodedPayload);
    }

    public Optional<Long> parseUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }

        try {
            String[] parts = token.split("\\.");
            if (parts.length != 2) {
                return Optional.empty();
            }

            byte[] expectedSignature = sign(parts[0]).getBytes(StandardCharsets.UTF_8);
            byte[] actualSignature = parts[1].getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expectedSignature, actualSignature)) {
                return Optional.empty();
            }

            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] payloadParts = payload.split(":");
            if (payloadParts.length != 2) {
                return Optional.empty();
            }

            long expiresAt = Long.parseLong(payloadParts[1]);
            if (expiresAt <= Instant.now().getEpochSecond()) {
                return Optional.empty();
            }
            return Optional.of(Long.parseLong(payloadParts[0]));
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(signingKey, HMAC_ALGORITHM));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("生成登录令牌失败", e);
        }
    }
}
