package com.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class WeRunCloudOpenDataServiceTest {

    @Test
    void exchangesCloudIdOverCloudHostingOpenApiAndParsesStringJson() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        WeRunDataDecryptor decryptor = mock(WeRunDataDecryptor.class);
        WeRunCloudOpenDataService service = service(builder, decryptor);
        var expected = new WeRunDataDecryptor.WeRunPayload(java.util.List.of(), 123);
        ObjectMapper objectMapper = new ObjectMapper();
        var envelope = objectMapper.readTree("{\"cloudID\":\"cloud-1\",\"data\":{\"stepInfoList\":[]}}");
        when(decryptor.parseCloudData(envelope)).thenReturn(expected);

        server.expect(requestTo("http://api.weixin.qq.com/wxa/getopendata?openid=openid-123"))
                .andExpect(method(POST))
                .andExpect(content().json("{\"cloudid_list\":[\"cloud-1\"]}"))
                .andRespond(withSuccess("{\"errcode\":0,\"errmsg\":\"ok\",\"data_list\":[{"
                        + "\"cloud_id\":\"cloud-1\",\"json\":\"{\\\"cloudID\\\":\\\"cloud-1\\\","
                        + "\\\"data\\\":{\\\"stepInfoList\\\":[]}}\"}]}", MediaType.TEXT_PLAIN));

        assertEquals(expected, service.fetch("cloud-1", "openid-123", "wx-app-id", "openid-123"));
        verify(decryptor).parseCloudData(envelope);
        server.verify();
    }

    @Test
    void rejectsIdentityThatWasNotInjectedForCurrentUser() {
        WeRunDataDecryptor decryptor = mock(WeRunDataDecryptor.class);
        WeRunCloudOpenDataService service = service(RestClient.builder(), decryptor);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.fetch("cloud-1", "another-openid", "wx-app-id", "openid-123"));

        assertEquals(ApiErrorCode.UNAUTHORIZED, error.getErrorCode());
        verifyNoInteractions(decryptor);
    }

    @Test
    void expiredCloudIdReturnsActionableClientError() {
        WeRunDataDecryptor decryptor = mock(WeRunDataDecryptor.class);
        WeRunCloudOpenDataService service = service(RestClient.builder(), decryptor);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.parseResponse("{\"errcode\":-601006,\"errmsg\":\"cloudID expired\"}", "cloud-1"));

        assertEquals(ApiErrorCode.INVALID_ARGUMENT, error.getErrorCode());
        assertEquals("微信运动授权已过期，请重新同步", error.getMessage());
    }

    private static WeRunCloudOpenDataService service(RestClient.Builder builder,
                                                     WeRunDataDecryptor decryptor) {
        WeRunCloudOpenDataService service = new WeRunCloudOpenDataService(builder, new ObjectMapper(), decryptor);
        ReflectionTestUtils.setField(service, "appId", "wx-app-id");
        ReflectionTestUtils.setField(service, "openDataUrl", "http://api.weixin.qq.com/wxa/getopendata");
        return service;
    }
}
