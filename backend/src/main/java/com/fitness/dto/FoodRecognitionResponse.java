package com.fitness.dto;

import java.math.BigDecimal;
import java.util.List;

/** Python Agent 返回的食物识别草稿；Java 只转发草稿，不在此阶段写入饮食记录。 */
public record FoodRecognitionResponse(
        String requestId,
        String status,
        String provider,
        List<FoodEstimate> foods,
        BigDecimal overallConfidence,
        boolean needsUserConfirmation,
        List<String> warnings,
        RecipeSuggestion recipeSuggestion,
        String recipeGenerationStatus) {

    public record FoodEstimate(
            String name,
            BigDecimal estimatedWeightGrams,
            List<BigDecimal> weightRangeGrams,
            String cookingMethod,
            NutritionEstimate nutrition,
            BigDecimal confidence) {}

    public record NutritionEstimate(
            BigDecimal caloriesKcal,
            BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams,
            BigDecimal fatGrams) {}

    /** 第二个模型只生成可编辑文案；不携带任何最终营养数据。 */
    public record RecipeSuggestion(
            String name,
            List<String> steps,
            BigDecimal confidence,
            String provider,
            String disclaimer) {}
}
