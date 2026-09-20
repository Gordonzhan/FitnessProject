package com.fitness.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/** 阶段分析使用包含首尾两天的明确业务日期范围。 */
public record AnalysisRangeRequest(
        @NotNull(message = "请选择开始日期") LocalDate startDate,
        @NotNull(message = "请选择结束日期") LocalDate endDate) {}
