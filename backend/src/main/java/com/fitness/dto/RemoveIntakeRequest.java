package com.fitness.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.time.LocalDate;

public record RemoveIntakeRequest(
        @NotNull(message = "请选择日期") LocalDate date,
        @NotNull(message = "请选择饮食记录") @Positive(message = "饮食记录编号无效") Long intakeId) {}
