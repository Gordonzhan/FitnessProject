package com.fitness.controller;

import com.fitness.auth.AuthTokenService;
import com.fitness.auth.WechatAccountService;
import com.fitness.auth.WechatLoginService;
import com.fitness.entity.User;
import com.fitness.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class UserControllerLoginTest {
    @Test
    void loginExchangesWechatCodeAndReturnsTheMappedProjectAccount() throws Exception {
        UserService users = mock(UserService.class);
        WechatLoginService wechatLogin = mock(WechatLoginService.class);
        WechatAccountService accounts = mock(WechatAccountService.class);
        AuthTokenService tokens = mock(AuthTokenService.class);
        User account = account();
        when(wechatLogin.resolveLoginOpenid("wx-code", null, null)).thenReturn("openid-private");
        when(accounts.getOrCreate("openid-private")).thenReturn(account);
        when(tokens.createToken(12L)).thenReturn("business-token");
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new UserController(users, wechatLogin, accounts, tokens))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/user/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"wx-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.token").value("business-token"))
                .andExpect(jsonPath("$.data.user.id").value(12))
                .andExpect(jsonPath("$.data.user.weight").value(70))
                .andExpect(jsonPath("$.data.user.openid").doesNotExist());

        verify(wechatLogin).resolveLoginOpenid("wx-code", null, null);
        verify(accounts).getOrCreate("openid-private");
        verify(tokens).createToken(12L);
    }

    @Test
    void loginPassesCloudbaseGatewayIdentityToTheResolver() throws Exception {
        UserService users = mock(UserService.class);
        WechatLoginService wechatLogin = mock(WechatLoginService.class);
        WechatAccountService accounts = mock(WechatAccountService.class);
        AuthTokenService tokens = mock(AuthTokenService.class);
        User account = account();
        when(wechatLogin.resolveLoginOpenid("unused-code", "openid-cloud", "wx-app"))
                .thenReturn("openid-cloud");
        when(accounts.getOrCreate("openid-cloud")).thenReturn(account);
        when(tokens.createToken(12L)).thenReturn("business-token");
        MockMvc mvc = MockMvcBuilders
                .standaloneSetup(new UserController(users, wechatLogin, accounts, tokens))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/user/login")
                        .header("X-WX-OPENID", "openid-cloud")
                        .header("X-WX-APPID", "wx-app")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"unused-code\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(wechatLogin).resolveLoginOpenid("unused-code", "openid-cloud", "wx-app");
        verify(accounts).getOrCreate("openid-cloud");
    }

    private User account() {
        User user = new User();
        user.setId(12L);
        user.setOpenid("openid-private");
        user.setGender("male");
        user.setAge(25);
        user.setHeight(new BigDecimal("175"));
        user.setWeight(new BigDecimal("70"));
        user.setGoal("maintain");
        user.setActivityLevel(new BigDecimal("1.375"));
        return user;
    }
}
