package com.fitness.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 微信运动按日汇总；估算消耗始终与训练实际消耗分开。 */
@Data
public class ActivityDailyRecord {
    private Long id;
    private Long userId;
    private LocalDate activityDate;
    private Integer steps;
    private BigDecimal estimatedCalories;
    private BigDecimal referenceWeight;
    private String source;
    private String estimationVersion;
    private Long sourceTimestamp;
    private LocalDateTime syncedAt;
}
