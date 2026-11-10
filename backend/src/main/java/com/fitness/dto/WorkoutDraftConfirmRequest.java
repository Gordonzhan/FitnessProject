package com.fitness.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WorkoutDraftConfirmRequest(
        @NotNull(message = "请选择训练日期") LocalDate date,
        @NotNull(message = "缺少待确认训练草稿") WorkoutDraftResponse draft) {}
