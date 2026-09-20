package com.fitness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** 在微信云托管私有链路内用短时效 CloudID 换取微信运动开放数据。 */
@Service
public class WeRunCloudOpenDataService {
    private static final Logger log = LoggerFactory.getLogger(WeRunCloudOpenDataService.class);
    private static final Pattern OPENID_PATTERN = Pattern.compile("[A-Za-z0-9_-]{6,50}");
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final WeRunDataDecryptor decryptor;

    @Value("${fitness.wechat.app-id:}")
    private String appId;

    @Value("${fitness.wechat.open-data-url:http://api.weixin.qq.com/wxa/getopendata}")
    private String openDataUrl;

    public WeRunCloudOpenDataService(RestClient.Builder restClientBuilder, ObjectMapper objectMapper,
                                     WeRunDataDecryptor decryptor) {
        this.restClient = restClientBuilder.build();
        this.objectMapper = objectMapper;
        this.decryptor = decryptor;
    }

    public WeRunDataDecryptor.WeRunPayload fetch(String cloudId, String gatewayOpenid,
                                                  String gatewayAppId, String expectedUserOpenid) {
        validateGatewayIdentity(cloudId, gatewayOpenid, gatewayAppId, expectedUserOpenid);
        URI uri = UriComponentsBuilder.fromUriString(openDataUrl)
                .queryParam("openid", gatewayOpenid)
                .build().encode().toUri();
        String responseBody;
        try {
            responseBody = restClient.post().uri(uri)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("cloudid_list", List.of(cloudId)))
                    .retrieve().body(String.class);
        } catch (Exception error) {
            log.warn("WECHAT_OPEN_DATA_UNAVAILABLE reason={} rootCause={}",
                    error.getClass().getSimpleName(), rootCauseType(error));
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "微信开放数据服务暂不可用，请稍后重试");
        }
        return parseResponse(responseBody, cloudId);
    }

    private void validateGatewayIdentity(String cloudId, String gatewayOpenid,
                                         String gatewayAppId, String expectedUserOpenid) {
        boolean valid = StringUtils.hasText(cloudId)
                && StringUtils.hasText(appId)
                && appId.equals(gatewayAppId)
                && StringUtils.hasText(gatewayOpenid)
                && OPENID_PATTERN.matcher(gatewayOpenid).matches()
                && gatewayOpenid.equals(expectedUserOpenid);
        if (!valid) {
            log.warn("WECHAT_OPEN_DATA_IDENTITY_REJECTED reason=invalid_gateway_identity");
            throw new BusinessException(ApiErrorCode.UNAUTHORIZED,
                    "微信云托管身份校验失败，请重新进入小程序");
        }
    }

    WeRunDataDecryptor.WeRunPayload parseResponse(String responseBody, String requestedCloudId) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            int errorCode = root.path("errcode").asInt(-1);
            if (errorCode != 0) {
                log.warn("WECHAT_OPEN_DATA_REJECTED errcode={} errmsg={}",
                        errorCode, safeErrorMessage(root.path("errmsg").asText("")));
                throw openDataFailure(errorCode);
            }
            JsonNode rows = root.path("data_list");
            if (!rows.isArray() || rows.isEmpty()) {
                throw new IllegalArgumentException("微信开放数据返回为空，请重新同步");
            }
            JsonNode item = rows.get(0);
            if (!requestedCloudId.equals(item.path("cloud_id").asText(""))) {
                throw new SecurityException("微信开放数据标识不匹配");
            }
            JsonNode json = item.path("json");
            JsonNode envelope = json.isTextual() ? objectMapper.readTree(json.asText()) : json;
            return decryptor.parseCloudData(envelope);
        } catch (BusinessException | SecurityException | IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            log.warn("WECHAT_OPEN_DATA_INVALID_RESPONSE reason={}", error.getClass().getSimpleName());
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "微信开放数据返回异常，请稍后重试");
        }
    }

    private RuntimeException openDataFailure(int errorCode) {
        if (errorCode == -601006 || errorCode == 40097) {
            return new BusinessException(ApiErrorCode.INVALID_ARGUMENT, "微信运动授权已过期，请重新同步");
        }
        return new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                "微信开放数据服务返回错误（" + errorCode + "）");
    }

    private static String safeErrorMessage(String value) {
        if (!StringUtils.hasText(value)) return "unknown";
        String sanitized = value.replaceAll("[^A-Za-z0-9_ .:-]", "?");
        return sanitized.substring(0, Math.min(sanitized.length(), 80));
    }

    private static String rootCauseType(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) current = current.getCause();
        return current.getClass().getSimpleName();
    }
}
