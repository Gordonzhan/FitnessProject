package com.fitness.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record WorkoutCompleteRequest(
        @NotNull(message = "实际训练消耗不能为空")
        @DecimalMin(value = "0", message = "实际训练消耗不能为负数")
        @DecimalMax(value = "99999999.99", message = "实际训练消耗数值过大")
        BigDecimal actualCalories) {}
