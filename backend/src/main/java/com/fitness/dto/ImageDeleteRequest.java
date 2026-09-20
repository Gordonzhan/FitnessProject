package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ImageDeleteRequest(
        @NotBlank(message = "图片地址不能为空") @Size(max = 2048, message = "图片地址过长") String imageUrl) {}
