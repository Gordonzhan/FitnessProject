package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ImageUploadConfirmRequest(
        @NotBlank(message = "上传授权编号不能为空")
        @Pattern(regexp = "[0-9a-fA-F-]{36}", message = "上传授权编号格式不正确")
        String ticketId,
        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "上传尝试编号格式不正确")
        String uploadAttemptId) {
}
