package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record WorkoutDraftRemoveRequest(
        @NotNull(message = "请选择训练日期") LocalDate date,
        @NotNull(message = "缺少当前训练草稿") WorkoutDraftResponse draft,
        @NotBlank(message = "请选择要删除的动作")
        @Pattern(regexp = "[A-Z0-9_]{1,32}", message = "动作编号格式无效") String exerciseCode) {}
