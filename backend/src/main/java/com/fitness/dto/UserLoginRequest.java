package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UserLoginRequest(
        @NotBlank(message = "微信登录凭证不能为空")
        @Size(max = 128, message = "微信登录凭证长度无效") String code) {}
