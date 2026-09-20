package com.fitness.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import org.springframework.util.StringUtils;

public record WeRunSyncRequest(
        @Size(max = 256, message = "微信登录凭证过长") String code,
        @Size(max = 1024, message = "微信运动CloudID过长") String cloudId,
        @Size(max = 65535, message = "微信运动加密数据过长") String encryptedData,
        @Size(max = 256, message = "微信运动初始向量过长") String iv
) {
    @JsonIgnore
    @AssertTrue(message = "微信运动授权数据不完整，请重新同步")
    public boolean isAuthorizationComplete() {
        return StringUtils.hasText(cloudId)
                || (StringUtils.hasText(code) && StringUtils.hasText(encryptedData) && StringUtils.hasText(iv));
    }
}
