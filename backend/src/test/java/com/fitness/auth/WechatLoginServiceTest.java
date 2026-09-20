package com.fitness.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class WechatLoginServiceTest {
    private final WechatLoginService service = service(RestClient.builder());

    @Test
    void acceptsWechatJsonReturnedAsTextPlain() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WechatLoginService service = service(builder);
        ReflectionTestUtils.setField(service, "mockEnabled", false);
        ReflectionTestUtils.setField(service, "appId", "test-app-id");
        ReflectionTestUtils.setField(service, "appSecret", "test-app-secret");

        server.expect(requestTo("https://api.weixin.qq.com/sns/jscode2session?appid=test-app-id&secret=test-app-secret&js_code=fresh-code&grant_type=authorization_code"))
                .andExpect(queryParam("js_code", "fresh-code"))
                .andRespond(withSuccess("{\"openid\":\"openid-1\",\"session_key\":\"session-1\"}", MediaType.TEXT_PLAIN));

        WechatLoginService.WechatSession session = service.exchangeCodeForSession("fresh-code");

        assertEquals("openid-1", session.openid());
        assertEquals("session-1", session.sessionKey());
        server.verify();
    }

    @Test
    void expiredOrConsumedCodeIsReportedAsReauthRequired() {
        for (int code : new int[]{40029, 40163}) {
            BusinessException error = assertInstanceOf(BusinessException.class, service.loginFailure(code));
            assertEquals(ApiErrorCode.UNAUTHORIZED, error.getErrorCode());
            assertEquals("微信登录临时凭证已失效，请重新进入小程序", error.getMessage());
        }
    }

    @Test
    void invalidAppCredentialsAreReportedWithoutLeakingSecret() {
        for (int code : new int[]{40013, 40125}) {
            BusinessException error = assertInstanceOf(BusinessException.class, service.loginFailure(code));
            assertEquals(ApiErrorCode.UPSTREAM_UNAVAILABLE, error.getErrorCode());
            assertEquals("微信小程序 AppID 或 AppSecret 配置不正确", error.getMessage());
        }
    }

    @Test
    void reportsOnlyTheDeepestNetworkExceptionType() {
        RuntimeException outer = new RuntimeException("URL containing secret",
                new IllegalStateException("nested", new SocketTimeoutException("timed out")));

        assertEquals("SocketTimeoutException", WechatLoginService.rootCauseType(outer));
        assertEquals("Unknown", WechatLoginService.rootCauseType(null));
    }

    @Test
    void acceptsCloudbaseGatewayIdentityWhenAppIdMatches() {
        ReflectionTestUtils.setField(service, "appId", "wx-app-id");

        assertEquals("openid_cloud-123",
                service.resolveLoginOpenid("unused-code", "openid_cloud-123", "wx-app-id"));
    }

    @Test
    void rejectsCloudbaseGatewayIdentityWhenAppIdDoesNotMatch() {
        ReflectionTestUtils.setField(service, "appId", "wx-app-id");

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.resolveLoginOpenid("unused-code", "openid_cloud-123", "other-app"));

        assertEquals(ApiErrorCode.UNAUTHORIZED, error.getErrorCode());
        assertTrue(error.getMessage().contains("身份校验失败"));
    }

    private static WechatLoginService service(RestClient.Builder builder) {
        return new WechatLoginService(builder, new ObjectMapper());
    }
}
