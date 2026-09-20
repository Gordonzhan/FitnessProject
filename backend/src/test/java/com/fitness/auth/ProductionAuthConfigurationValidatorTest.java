package com.fitness.auth;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionAuthConfigurationValidatorTest {
    @Test
    void localModeDoesNotRequireProductionSecrets() {
        ProductionAuthConfigurationValidator validator = validator(false, true, "", "", "");
        assertDoesNotThrow(validator::validate);
    }

    @Test
    void productionModeRejectsMockLoginAndMissingOrWeakSecrets() {
        ProductionAuthConfigurationValidator validator = validator(true, true, "", "", "short");

        IllegalStateException error = assertThrows(IllegalStateException.class, validator::validate);

        assertTrue(error.getMessage().contains("必须关闭微信模拟登录"));
        assertTrue(error.getMessage().contains("必须配置微信小程序 AppID"));
        assertTrue(error.getMessage().contains("必须配置微信小程序 AppSecret"));
        assertTrue(error.getMessage().contains("至少 32 字节"));
        assertTrue(!error.getMessage().contains("short"));
    }

    @Test
    void productionModeAcceptsCompleteAuthConfiguration() {
        ProductionAuthConfigurationValidator validator = validator(
                true, false, "wx-app-id", "wx-app-secret", "0123456789abcdef0123456789abcdef");
        assertDoesNotThrow(validator::validate);
    }

    private ProductionAuthConfigurationValidator validator(boolean productionMode,
                                                           boolean mockEnabled,
                                                           String appId,
                                                           String appSecret,
                                                           String tokenSecret) {
        ProductionAuthConfigurationValidator validator = new ProductionAuthConfigurationValidator();
        ReflectionTestUtils.setField(validator, "productionMode", productionMode);
        ReflectionTestUtils.setField(validator, "wechatMockEnabled", mockEnabled);
        ReflectionTestUtils.setField(validator, "wechatAppId", appId);
        ReflectionTestUtils.setField(validator, "wechatAppSecret", appSecret);
        ReflectionTestUtils.setField(validator, "tokenSecret", tokenSecret);
        return validator;
    }
}
