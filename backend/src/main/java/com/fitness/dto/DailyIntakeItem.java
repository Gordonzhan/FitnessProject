package com.fitness.dto;

import com.fitness.entity.DailyIntake;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** 单次饮食记录的展示数据；intakeId 与菜谱业务 recipeId 分开。 */
public record DailyIntakeItem(String intakeId, String recipeId, String mealType,
                              String mealSlot, String mealSlotLabel, BigDecimal servingCount,
                              String consumedTime,
                              BigDecimal protein, BigDecimal carb, BigDecimal fat, BigDecimal calorie) {
    public static DailyIntakeItem from(DailyIntake intake) {
        return new DailyIntakeItem(intake.getId().toString(), intake.getRecipeBusinessId(),
                intake.getMealTypeSnapshot(), intake.getMealSlot(), mealSlotLabel(intake.getMealSlot()),
                intake.getServingCount(), intake.getConsumedTime() == null ? ""
                        : intake.getConsumedTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                intake.getProteinSnapshot(), intake.getCarbSnapshot(),
                intake.getFatSnapshot(), intake.getCalorieSnapshot());
    }

    private static String mealSlotLabel(String value) {
        if (value == null) return "";
        return switch (value) {
            case "BREAKFAST" -> "早餐";
            case "LUNCH" -> "午餐";
            case "DINNER" -> "晚餐";
            case "SNACK" -> "加餐";
            default -> "";
        };
    }
}
