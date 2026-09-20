package com.fitness.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class WechatLoginService {
    private static final Logger log = LoggerFactory.getLogger(WechatLoginService.class);
    private static final Pattern OPENID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{6,50}");
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public WechatLoginService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
    }

    @Value("${fitness.wechat.mock-enabled:true}")
    private boolean mockEnabled;

    @Value("${fitness.wechat.mock-openid:test_openid}")
    private String mockOpenid;

    @Value("${fitness.wechat.app-id:}")
    private String appId;

    @Value("${fitness.wechat.app-secret:}")
    private String appSecret;

    public String exchangeCodeForOpenid(String code) {
        return exchangeCodeForSession(code).openid();
    }

    /**
     * 云托管 callContainer 请求由微信网关注入 OpenID 和 AppID。AppID 必须与服务端配置一致，
     * 因此不接受客户端正文自行声明身份；非云托管请求继续使用一次性 code 换取 OpenID。
     */
    public String resolveLoginOpenid(String code, String cloudOpenid, String cloudAppId) {
        boolean hasCloudIdentity = StringUtils.hasText(cloudOpenid) || StringUtils.hasText(cloudAppId);
        if (!hasCloudIdentity) {
            return exchangeCodeForOpenid(code);
        }
        if (!StringUtils.hasText(appId)
                || !appId.equals(cloudAppId)
                || !StringUtils.hasText(cloudOpenid)
                || !OPENID_PATTERN.matcher(cloudOpenid).matches()) {
            log.warn("WECHAT_CLOUD_IDENTITY_REJECTED reason=invalid_gateway_identity");
            throw new BusinessException(ApiErrorCode.UNAUTHORIZED, "微信云托管身份校验失败，请重新进入小程序");
        }
        return cloudOpenid;
    }

    /** 使用一次性 code 换取微信会话；sessionKey 只供本次开放数据解密，不写入数据库或日志。 */
    public WechatSession exchangeCodeForSession(String code) {
        if (mockEnabled) {
            return new WechatSession(mockOpenid, null);
        }

        if (!StringUtils.hasText(code)) {
            throw new IllegalArgumentException("微信登录凭证不能为空");
        }
        if (!StringUtils.hasText(appId) || !StringUtils.hasText(appSecret)) {
            throw new IllegalStateException("服务端未配置微信小程序 AppID 或 AppSecret");
        }

        URI uri = UriComponentsBuilder
                .fromHttpUrl("https://api.weixin.qq.com/sns/jscode2session")
                .queryParam("appid", appId)
                .queryParam("secret", appSecret)
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUri();

        String responseBody;
        try {
            // 微信偶尔以 text/plain 返回 JSON；先读字符串，避免按 Map 反序列化时因 Content-Type 失败。
            responseBody = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (Exception error) {
            // 不记录异常 message：RestClient 的 message 可能包含带 AppSecret 和一次性 code 的完整 URL。
            // 仅记录外层及最深层异常类型，用于区分 DNS、连接、超时和 TLS 故障。
            log.warn("WECHAT_CODE_SESSION_UNAVAILABLE reason={} rootCause={}",
                    error.getClass().getSimpleName(), rootCauseType(error));
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "暂时无法连接微信登录服务，请稍后重试");
        }
        Map<?, ?> result = parseResponse(responseBody);
        if (result == null || !StringUtils.hasText((String) result.get("openid"))) {
            Object errorCode = result == null ? null : result.get("errcode");
            Object errorMessage = result == null ? null : result.get("errmsg");
            log.warn("WECHAT_CODE_SESSION_REJECTED errcode={} errmsg={}", errorCode, errorMessage);
            throw loginFailure(errorCode);
        }
        return new WechatSession((String) result.get("openid"), (String) result.get("session_key"));
    }

    Map<?, ?> parseResponse(String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(responseBody, Map.class);
        } catch (JsonProcessingException error) {
            log.warn("WECHAT_CODE_SESSION_INVALID_RESPONSE reason={}", error.getClass().getSimpleName());
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "微信登录服务返回异常，请稍后重试");
        }
    }

    /** 将微信错误转换为可操作但不泄露凭证的统一业务错误。 */
    RuntimeException loginFailure(Object errorCode) {
        int code;
        try {
            code = Integer.parseInt(String.valueOf(errorCode));
        } catch (Exception ignored) {
            code = 0;
        }
        if (code == 40029 || code == 40163) {
            return new BusinessException(ApiErrorCode.UNAUTHORIZED, "微信登录临时凭证已失效，请重新进入小程序");
        }
        if (code == 40013 || code == 40125) {
            return new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "微信小程序 AppID 或 AppSecret 配置不正确");
        }
        return new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                code == 0 ? "微信登录服务返回异常，请稍后重试" : "微信登录服务返回错误（" + code + "）");
    }

    static String rootCauseType(Throwable error) {
        if (error == null) return "Unknown";
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current.getClass().getSimpleName();
    }

    public record WechatSession(String openid, String sessionKey) {}
}
