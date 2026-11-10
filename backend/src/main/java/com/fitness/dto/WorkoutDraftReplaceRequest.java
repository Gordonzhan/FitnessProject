package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record WorkoutDraftReplaceRequest(
        @NotNull(message = "请选择训练日期") LocalDate date,
        @NotNull(message = "缺少当前训练草稿") WorkoutDraftResponse draft,
        @NotBlank(message = "缺少本次训练需求")
        @Size(max = 500, message = "训练需求最多500字") String message,
        @NotBlank(message = "请选择要替换的动作")
        @Pattern(regexp = "[A-Z0-9_]{1,32}", message = "动作编号格式无效") String exerciseCode) {}
