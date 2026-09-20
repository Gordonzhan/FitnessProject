package com.fitness.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 手动补录一条体重历史，同日重复提交会保留为独立记录。 */
public record WeightRecordRequest(
        @NotNull(message = "请选择体重记录日期") LocalDate date,
        @NotNull(message = "请填写体重")
        @DecimalMin(value = "25", message = "体重不能小于25公斤")
        @DecimalMax(value = "350", message = "体重不能大于350公斤") BigDecimal weight) {}
