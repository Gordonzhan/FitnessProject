package com.fitness.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** R6 微信运动步数及独立估算值，不参与 R5 能量差。 */
public record ActivitySummary(
        LocalDate startDate,
        LocalDate endDate,
        String source,
        String sourceLabel,
        String estimationVersion,
        String estimationFormula,
        LocalDateTime lastSyncedAt,
        int recordedDays,
        List<DailyPoint> dailyPoints,
        List<String> notices
) {
    public record DailyPoint(LocalDate date, int steps, BigDecimal estimatedCalories,
                             BigDecimal referenceWeight) {}
}
