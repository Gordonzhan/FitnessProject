package com.fitness.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 用户确认 AI 草稿后，原子保存菜谱并生成一条当日饮食快照。 */
public record RecipeIntakeConfirmRequest(
        @NotNull(message = "菜谱不能为空") @Valid RecipeSaveRequest recipe,
        @NotNull(message = "请选择入账日期") LocalDate date,
        @NotBlank(message = "请求编号不能为空")
        @Size(max = 64, message = "请求编号不能超过64个字符")
        @Pattern(regexp = "[A-Za-z0-9_-]+", message = "请求编号格式无效") String requestId,
        @NotBlank(message = "请选择餐次")
        @Pattern(regexp = "BREAKFAST|LUNCH|DINNER|SNACK", message = "餐次无效") String mealSlot,
        @NotNull(message = "请输入食用份数")
        @DecimalMin(value = "0.25", message = "食用份数至少为0.25")
        @DecimalMax(value = "10", message = "食用份数不能超过10") BigDecimal servingCount,
        @NotNull(message = "请选择食用时间") LocalTime consumedTime) {}
