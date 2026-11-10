package com.fitness.ai;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

/** 生成 Python 只能转交、不能改写的短时效当前用户上下文。 */
@Service
public class AgentUserContextService {
    private final byte[] secret;
    private final long ttlSeconds;

    public AgentUserContextService(@Value("${fitness.agent.context-secret:}") String configuredSecret,
                                   @Value("${fitness.agent.context-ttl-seconds:300}") long ttlSeconds) {
        if (ttlSeconds < 30 || ttlSeconds > 900) throw new IllegalArgumentException("Agent用户上下文有效期不合法");
        if (StringUtils.hasText(configuredSecret)) {
            this.secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
        } else {
            this.secret = new byte[32];
            new SecureRandom().nextBytes(this.secret);
        }
        this.ttlSeconds = ttlSeconds;
    }

    public String issue(Long userId) {
        String payload = userId + ":" + Instant.now().plusSeconds(ttlSeconds).getEpochSecond() + ":"
                + HexFormat.of().formatHex(randomBytes(12));
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        return encoded + "." + sign(encoded);
    }

    public Long verify(String token) {
        if (!StringUtils.hasText(token) || !token.contains(".")) throw new SecurityException("Agent用户上下文缺失");
        String[] parts = token.split("\\.", 2);
        if (!java.security.MessageDigest.isEqual(sign(parts[0]).getBytes(StandardCharsets.US_ASCII),
                parts[1].getBytes(StandardCharsets.US_ASCII))) throw new SecurityException("Agent用户上下文签名无效");
        try {
            String payload = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            String[] fields = payload.split(":", 3);
            if (Instant.now().getEpochSecond() > Long.parseLong(fields[1])) {
                throw new SecurityException("Agent用户上下文已过期");
            }
            return Long.parseLong(fields[0]);
        } catch (IllegalArgumentException | ArrayIndexOutOfBoundsException error) {
            throw new SecurityException("Agent用户上下文格式无效");
        }
    }

    private String sign(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("无法签发Agent用户上下文", error);
        }
    }

    private byte[] randomBytes(int size) {
        byte[] value = new byte[size]; new SecureRandom().nextBytes(value); return value;
    }
}
