package com.fitness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ImageUploadTicketRequest(
        @Min(value = 1, message = "图片不能为空")
        @Max(value = 20L * 1024 * 1024, message = "单张图片不能超过20MB")
        long fileSize,
        @NotBlank(message = "图片格式不能为空")
        @Pattern(regexp = "(?i)jpe?g|png|gif|webp", message = "仅支持 JPG、PNG、GIF、WebP 图片")
        String format,
        @Pattern(regexp = "[A-Za-z0-9_-]{8,64}", message = "上传尝试编号格式不正确")
        String uploadAttemptId) {
}
