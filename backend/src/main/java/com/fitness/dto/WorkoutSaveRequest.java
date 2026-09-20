package com.fitness.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import com.fitness.entity.WorkoutStatus;

public record WorkoutSaveRequest(
        @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "训练编号格式无效") String workoutId,
        @NotNull(message = "请选择训练日期") LocalDate date,
        WorkoutStatus status,
        @NotNull(message = "训练消耗不能为空") @DecimalMin(value = "0", message = "训练消耗不能为负数")
        @DecimalMax(value = "99999999.99", message = "训练消耗数值过大") BigDecimal totalCalories,
        @NotEmpty(message = "请至少添加一个训练动作") @Size(max = 100, message = "每次训练最多保存100个动作")
        List<@Valid ExerciseRequest> exercises) {

    public record ExerciseRequest(
            @NotBlank(message = "动作名称不能为空") @Size(max = 50, message = "动作名称不能超过50个字符") String name,
            @DecimalMin(value = "0", message = "训练重量不能为负数")
            @DecimalMax(value = "99999999.99", message = "训练重量数值过大") BigDecimal weight,
            @NotNull(message = "训练组数不能为空") @Min(value = 1, message = "训练组数至少为1")
            @Max(value = 100, message = "训练组数不能超过100") Integer sets,
            @NotNull(message = "每组次数不能为空") @Min(value = 1, message = "每组次数至少为1")
            @Max(value = 1000, message = "每组次数不能超过1000") Integer reps,
            @NotNull(message = "动作消耗不能为空") @DecimalMin(value = "0", message = "动作消耗不能为负数")
            @DecimalMax(value = "99999999.99", message = "动作消耗数值过大") BigDecimal calories) {}
}
