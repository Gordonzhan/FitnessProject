package com.fitness.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 写入独立匿名对比表的日级样本。 */
@Data
public class ComparisonDailySample {
    private String participantKey;
    private String maskedDisplayName;
    private LocalDate sampleDate;
    private String goal;
    private Integer completedSessions;
    private Integer plannedSessions;
    private Integer cancelledSessions;
    private BigDecimal actualCalories;
    private String source;
    private String datasetVersion;
}
