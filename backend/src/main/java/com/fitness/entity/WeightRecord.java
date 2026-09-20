package com.fitness.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 用户体重历史；同一天允许多次记录，阶段趋势按当天最后一条计算。 */
@Data
public class WeightRecord {
    private Long id;
    private Long userId;
    private LocalDate recordDate;
    private BigDecimal weight;
    private String source;
    private LocalDateTime recordedAt;
}
