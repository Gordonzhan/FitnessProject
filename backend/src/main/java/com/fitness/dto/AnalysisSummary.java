package com.fitness.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** R5 个人阶段性饮食与训练分析的有界聚合响应。 */
public record AnalysisSummary(
        LocalDate startDate,
        LocalDate endDate,
        int totalDays,
        String dataState,
        List<DailyPoint> dailyPoints,
        List<WeightPoint> weightPoints,
        Averages averages,
        TrainingCompletion trainingCompletion,
        MacroReference macroReference,
        GoalAssessment goalAssessment,
        List<String> notices) {

    public record DailyPoint(
            LocalDate date,
            BigDecimal intakeCalories,
            BigDecimal actualExpenditure,
            BigDecimal energyDifference,
            BigDecimal targetDifference,
            BigDecimal protein,
            BigDecimal carb,
            BigDecimal fat,
            boolean hasNutrition,
            boolean hasWorkout) {}

    public record WeightPoint(LocalDate date, BigDecimal weight) {}

    public record Averages(
            BigDecimal intakeCalories,
            BigDecimal actualExpenditure,
            BigDecimal energyDifference,
            BigDecimal targetDifference,
            BigDecimal protein,
            BigDecimal carb,
            BigDecimal fat,
            int nutritionRecordedDays,
            int workoutRecordedDays) {}

    public record TrainingCompletion(int completed, int planned, int cancelled, BigDecimal rate) {}

    public record MacroRange(BigDecimal min, BigDecimal max, String unit) {}

    public record MacroReference(
            BigDecimal referenceWeight,
            MacroRange protein,
            MacroRange carb,
            MacroRange fat,
            String note) {}

    public record GoalAssessment(
            String status,
            String title,
            String detail,
            BigDecimal weightChange,
            boolean hasWeightTrend) {}
}
