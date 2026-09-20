package com.fitness.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 饮食快照按业务日期聚合后的内部查询结果。 */
@Data
public class DailyNutritionSummary {
    private LocalDate date;
    private BigDecimal calories;
    private BigDecimal protein;
    private BigDecimal carb;
    private BigDecimal fat;
    private Integer recordCount;
}
