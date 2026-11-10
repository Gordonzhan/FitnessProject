package com.fitness.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * 可直接交给小程序编辑的识别草稿。模型估算与数据库匹配结果分开表达，
 * 避免把模型生成的营养值误当成业务权威数据。
 */
public record FoodRecognitionDraftResponse(
        String requestId,
        String status,
        String provider,
        List<DraftItem> items,
        BigDecimal overallConfidence,
        boolean needsUserConfirmation,
        List<String> warnings,
        RecipeSuggestion recipeSuggestion,
        String recipeGenerationStatus) {

    public record DraftItem(
            String recognizedName,
            BigDecimal estimatedWeightGrams,
            BigDecimal minimumWeightGrams,
            BigDecimal maximumWeightGrams,
            String cookingMethod,
            BigDecimal confidence,
            String matchStatus,
            FoodOption matchedFood,
            List<FoodOption> candidates,
            Nutrition nutrition) {}

    /** 仅返回编辑草稿所需字段，不向客户端暴露数据源内部细节。 */
    public record FoodOption(
            Long id,
            String foodName,
            String category,
            String servingState,
            BigDecimal proteinPer100g,
            BigDecimal carbohydratePer100g,
            BigDecimal fatPer100g,
            BigDecimal caloriesPer100g,
            boolean genericFallback) {}

    public record Nutrition(
            BigDecimal caloriesKcal,
            BigDecimal proteinGrams,
            BigDecimal carbohydrateGrams,
            BigDecimal fatGrams) {}

    /** 透传给小程序编辑的 AI 菜名和步骤建议；用户确认前不会保存。 */
    public record RecipeSuggestion(
            String name,
            List<String> steps,
            BigDecimal confidence,
            String provider,
            String disclaimer) {}
}
