package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.service.ActivityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ActivityControllerTest {
    private final ActivityService activity = mock(ActivityService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ActivityController(activity))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void syncUsesAuthenticatedUserAndValidatedEncryptedPayload() throws Exception {
        mvc.perform(post("/activity/wechat/sync")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"c\",\"encryptedData\":\"data\",\"iv\":\"iv\",\"userId\":999}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(activity).syncWechatSteps(eq(7L), any(), isNull(), isNull());
    }

    @Test
    void cloudIdSyncPassesOnlyGatewayInjectedIdentityToService() throws Exception {
        mvc.perform(post("/activity/wechat/sync")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .header("X-WX-OPENID", "openid-cloud")
                        .header("X-WX-APPID", "wx-app-id")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cloudId\":\"cloud-id\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200));
        verify(activity).syncWechatSteps(eq(7L), any(), eq("openid-cloud"), eq("wx-app-id"));
    }

    @Test
    void missingSensitiveFieldsNeverReachesService() throws Exception {
        mvc.perform(post("/activity/wechat/sync")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(activity);
    }

    @Test
    void readAndDeleteAreScopedToAuthenticatedUser() throws Exception {
        mvc.perform(get("/activity/wechat/summary").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk());
        mvc.perform(delete("/activity/wechat").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk());
        verify(activity).summary(7L);
        verify(activity).deleteWechatData(7L);
    }
}
