package com.fitness.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 编辑页只提交动作事实，预计消耗由 Java 动作库、参考体重和计划时长统一计算。 */
public record WorkoutEstimateRequest(
        @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "训练编号格式无效") String workoutId,
        @Min(value = 1, message = "训练预计时长至少为1分钟")
        @Max(value = 1440, message = "训练预计时长不能超过1440分钟") Integer estimatedDurationMinutes,
        @NotEmpty(message = "请至少添加一个训练动作") @Size(max = 100, message = "每次训练最多保存100个动作")
        List<@Valid ExerciseInput> exercises) {

    public record ExerciseInput(
            @NotBlank(message = "动作名称不能为空") @Size(max = 50, message = "动作名称不能超过50个字符") String name,
            @DecimalMin(value = "0", message = "训练重量不能为负数")
            @DecimalMax(value = "99999999.99", message = "训练重量数值过大") BigDecimal weight,
            @Min(value = 1, message = "训练组数至少为1") @Max(value = 100, message = "训练组数不能超过100") int sets,
            @Min(value = 1, message = "每组次数至少为1") @Max(value = 1000, message = "每组次数不能超过1000") int reps,
            @Min(value = 1, message = "动作计算时长至少为1秒")
            @Max(value = 86400, message = "动作计算时长不能超过86400秒") Integer calorieDurationSeconds,
            @Min(value = 1, message = "动作时长基准组数至少为1")
            @Max(value = 100, message = "动作时长基准组数不能超过100") Integer calorieBasisSets) {
        public ExerciseInput(String name, BigDecimal weight, int sets, int reps) {
            this(name, weight, sets, reps, null, null);
        }
    }
}
