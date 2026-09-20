package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AddIntakeRequest(
        @NotNull(message = "请选择日期") LocalDate date,
        @NotBlank(message = "请选择菜谱")
        @Pattern(regexp = "(?:recipe_)?[A-Za-z0-9_-]{1,50}", message = "菜谱编号格式无效") String recipeId,
        @Size(max = 64, message = "请求编号不能超过64个字符") String requestId) {}
