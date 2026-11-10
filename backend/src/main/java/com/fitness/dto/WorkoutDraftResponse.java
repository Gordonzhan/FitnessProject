package com.fitness.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** 经 Java 规则校验和动作库补全后返回给小程序的训练草稿。 */
public record WorkoutDraftResponse(
        String requestId,
        String status,
        String provider,
        String title,
        String assistantReply,
        String userRequest,
        LocalDate date,
        Integer estimatedDurationMinutes,
        Integer targetRpe,
        List<ExerciseDraft> exercises,
        List<String> warnings,
        String disclaimer,
        Integer correctionCount,
        NutritionPlan nutritionPlan,
        List<String> sessionExcludedExerciseCodes) {

    public record ExerciseDraft(
            String exerciseCode,
            String name,
            String equipment,
            String primaryMuscles,
            Integer sets,
            Integer reps,
            Integer durationSeconds,
            Integer restSeconds,
            Integer rpe,
            BigDecimal weight,
            String loadType,
            String weightSource,
            String weightNote,
            BigDecimal calories,
            String rationale) {}

    /** 饮食只作为待确认建议展示，不写入已摄入记录。 */
    public record NutritionPlan(
            BigDecimal dailyTargetCalories,
            BigDecimal consumedCalories,
            BigDecimal remainingCalories,
            BigDecimal plannedCalories,
            BigDecimal plannedProtein,
            List<MealDraft> meals,
            String note) {}

    public record MealDraft(
            String mealSlot,
            String recipeId,
            String name,
            BigDecimal calories,
            BigDecimal protein,
            BigDecimal carb,
            BigDecimal fat,
            String servingDescription,
            String allergenInfo) {}
}
