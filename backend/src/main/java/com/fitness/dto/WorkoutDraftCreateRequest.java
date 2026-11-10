package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record WorkoutDraftCreateRequest(
        @NotNull(message = "请选择训练日期") LocalDate date,
        @NotBlank(message = "请告诉助手今天想怎么练")
        @Size(max = 500, message = "训练需求最多500字") String message,
        WorkoutDraftResponse currentDraft) {}
