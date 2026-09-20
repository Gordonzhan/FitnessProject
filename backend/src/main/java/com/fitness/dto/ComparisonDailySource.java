package com.fitness.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 生成正式匿名快照时使用的短生命周期日聚合，不对外响应。 */
@Data
public class ComparisonDailySource {
    private Long userId;
    private String displayName;
    private LocalDate sampleDate;
    private String goal;
    private Integer completedSessions;
    private Integer plannedSessions;
    private Integer cancelledSessions;
    private BigDecimal actualCalories;
}
