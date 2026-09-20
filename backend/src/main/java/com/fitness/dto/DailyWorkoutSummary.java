package com.fitness.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 训练状态及已完成实际消耗按业务日期聚合后的内部查询结果。 */
@Data
public class DailyWorkoutSummary {
    private LocalDate date;
    private BigDecimal actualCalories;
    private Integer completedCount;
    private Integer plannedCount;
    private Integer cancelledCount;
}
