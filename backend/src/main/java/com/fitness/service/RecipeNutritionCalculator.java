package com.fitness.service;

import com.fitness.entity.FoodDatabase;
import com.fitness.entity.Ingredient;
import com.fitness.entity.Recipe;
import com.fitness.repository.FoodDatabaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 保存前使用食材库快照重算每一行和总营养，客户端计算值仅用于即时预览。 */
@Service
@Transactional(readOnly = true)
public class RecipeNutritionCalculator {
    private final FoodDatabaseRepository foodRepository;

    public RecipeNutritionCalculator(FoodDatabaseRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    public void recalculate(Recipe recipe) {
        List<Ingredient> ingredients = recipe.getIngredients();
        List<Long> ids = ingredients.stream().map(Ingredient::getFoodDatabaseId)
                .filter(java.util.Objects::nonNull).distinct().toList();
        Map<Long, FoodDatabase> foodsById = new LinkedHashMap<>();
        foodRepository.findAllById(ids).forEach(food -> foodsById.put(food.getId(), food));
        List<String> names = ingredients.stream().filter(item -> item.getFoodDatabaseId() == null)
                .map(Ingredient::getFoodName).distinct().toList();
        Map<String, FoodDatabase> foodsByName = new LinkedHashMap<>();
        foodRepository.findByFoodNameInAndEnabledTrue(names)
                .forEach(food -> foodsByName.put(food.getFoodName(), food));

        BigDecimal protein = BigDecimal.ZERO;
        BigDecimal carb = BigDecimal.ZERO;
        BigDecimal fat = BigDecimal.ZERO;
        BigDecimal calorie = BigDecimal.ZERO;
        for (Ingredient ingredient : ingredients) {
            FoodDatabase food = ingredient.getFoodDatabaseId() == null
                    ? foodsByName.get(ingredient.getFoodName())
                    : foodsById.get(ingredient.getFoodDatabaseId());
            if (food == null) {
                throw new IllegalArgumentException("食材不在基础数据库中：" + ingredient.getFoodName());
            }
            if (Boolean.FALSE.equals(food.getEnabled())) {
                throw new IllegalArgumentException("食材已停用：" + ingredient.getFoodName());
            }
            // 标准食材 ID 是计算权威；名称作为保存快照由服务端覆盖，不能由客户端伪造。
            ingredient.setFoodDatabaseId(food.getId());
            ingredient.setFoodName(food.getFoodName());
            if (ingredient.getMappingSource() == null || ingredient.getMappingSource().isBlank()) {
                ingredient.setMappingSource("MANUAL");
            }
            BigDecimal ratio = ingredient.getWeight().divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
            ingredient.setProtein(scale(food.getProtein().multiply(ratio)));
            ingredient.setCarb(scale(food.getCarb().multiply(ratio)));
            ingredient.setFat(scale(food.getFat().multiply(ratio)));
            ingredient.setCalorie(scale(food.getCalorie().multiply(ratio)));
            protein = protein.add(ingredient.getProtein());
            carb = carb.add(ingredient.getCarb());
            fat = fat.add(ingredient.getFat());
            calorie = calorie.add(ingredient.getCalorie());
        }
        recipe.setProtein(scale(protein));
        recipe.setCarb(scale(carb));
        recipe.setFat(scale(fat));
        recipe.setCalorie(scale(calorie));
    }

    /** 与现有小程序显示精度一致，逐项保留一位后再汇总，确保保存结果可解释。 */
    private BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }
}
