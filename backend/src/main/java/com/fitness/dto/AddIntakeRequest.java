package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public record AddIntakeRequest(
        @NotNull(message = "请选择日期") LocalDate date,
        @NotBlank(message = "请选择菜谱")
        @Pattern(regexp = "(?:recipe_)?[A-Za-z0-9_-]{1,50}", message = "菜谱编号格式无效") String recipeId,
        @Size(max = 64, message = "请求编号不能超过64个字符") String requestId,
        @Pattern(regexp = "BREAKFAST|LUNCH|DINNER|SNACK", message = "餐次无效") String mealSlot,
        @DecimalMin(value = "0.25", message = "食用份数至少为0.25")
        @DecimalMax(value = "10", message = "食用份数不能超过10") BigDecimal servingCount,
        LocalTime consumedTime) {}
