package com.fitness.dto;

import lombok.Data;

import java.math.BigDecimal;

/** 服务端内部匿名参与者聚合；不得直接作为接口响应。 */
@Data
public class ComparisonParticipantStats {
    private String participantKey;
    private String maskedDisplayName;
    private String goal;
    private String source;
    private Integer completedCount;
    private Integer activeDays;
    private Integer plannedCount;
    private Integer cancelledCount;
    private BigDecimal actualCalories;
}
