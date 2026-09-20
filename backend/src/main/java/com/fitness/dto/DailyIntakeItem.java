package com.fitness.dto;

import com.fitness.entity.DailyIntake;
import java.math.BigDecimal;

/** 单次饮食记录的展示数据；intakeId 与菜谱业务 recipeId 分开。 */
public record DailyIntakeItem(String intakeId, String recipeId, String mealType,
                              BigDecimal protein, BigDecimal carb, BigDecimal fat, BigDecimal calorie) {
    public static DailyIntakeItem from(DailyIntake intake) {
        return new DailyIntakeItem(intake.getId().toString(), intake.getRecipeBusinessId(),
                intake.getMealTypeSnapshot(), intake.getProteinSnapshot(), intake.getCarbSnapshot(),
                intake.getFatSnapshot(), intake.getCalorieSnapshot());
    }
}
