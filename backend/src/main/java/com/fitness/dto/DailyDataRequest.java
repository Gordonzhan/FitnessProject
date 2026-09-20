package com.fitness.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record DailyDataRequest(@NotNull(message = "请选择日期") LocalDate date) {}
