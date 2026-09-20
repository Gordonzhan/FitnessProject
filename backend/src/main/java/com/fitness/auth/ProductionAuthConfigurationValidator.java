package com.fitness.auth;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** 生产模式启动前校验微信登录与业务 Token 的最低安全配置。 */
@Component
public class ProductionAuthConfigurationValidator {
    @Value("${fitness.production-mode:false}")
    private boolean productionMode;

    @Value("${fitness.wechat.mock-enabled:true}")
    private boolean wechatMockEnabled;

    @Value("${fitness.wechat.app-id:}")
    private String wechatAppId;

    @Value("${fitness.wechat.app-secret:}")
    private String wechatAppSecret;

    @Value("${fitness.auth.token-secret:}")
    private String tokenSecret;

    @PostConstruct
    void validate() {
        if (!productionMode) return;

        List<String> problems = new ArrayList<>();
        if (wechatMockEnabled) problems.add("必须关闭微信模拟登录");
        if (!StringUtils.hasText(wechatAppId)) problems.add("必须配置微信小程序 AppID");
        if (!StringUtils.hasText(wechatAppSecret)) problems.add("必须配置微信小程序 AppSecret");
        if (!StringUtils.hasText(tokenSecret)
                || tokenSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            problems.add("业务 Token 签名密钥必须至少 32 字节");
        }

        if (!problems.isEmpty()) {
            throw new IllegalStateException("生产认证配置不安全：" + String.join("；", problems));
        }
    }
}
