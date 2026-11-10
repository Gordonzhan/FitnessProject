package com.fitness.service;

import com.fitness.entity.FoodDatabase;
import com.fitness.entity.Ingredient;
import com.fitness.entity.Recipe;
import com.fitness.repository.FoodDatabaseRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RecipeNutritionCalculatorTest {
    private final FoodDatabaseRepository foods = mock(FoodDatabaseRepository.class);
    private final RecipeNutritionCalculator calculator = new RecipeNutritionCalculator(foods);

    @Test
    void clientNutritionIsOverwrittenByDatabaseValues() {
        FoodDatabase rice = new FoodDatabase();
        rice.setFoodName("米饭");
        rice.setProtein(new BigDecimal("2.60"));
        rice.setCarb(new BigDecimal("25.90"));
        rice.setFat(new BigDecimal("0.30"));
        rice.setCalorie(new BigDecimal("116.00"));
        when(foods.findByFoodNameInAndEnabledTrue(List.of("米饭"))).thenReturn(List.of(rice));
        Ingredient ingredient = ingredient("米饭", "150");
        ingredient.setCalorie(new BigDecimal("9999"));
        Recipe recipe = new Recipe();
        recipe.setIngredients(List.of(ingredient));

        calculator.recalculate(recipe);

        assertEquals(new BigDecimal("174.0"), ingredient.getCalorie());
        assertEquals(new BigDecimal("174.0"), recipe.getCalorie());
        assertEquals(new BigDecimal("38.9"), recipe.getCarb());
    }

    @Test
    void unknownFoodCannotBeSaved() {
        when(foods.findByFoodNameInAndEnabledTrue(List.of("模型编造食材"))).thenReturn(List.of());
        Recipe recipe = new Recipe();
        recipe.setIngredients(List.of(ingredient("模型编造食材", "100")));

        assertThrows(IllegalArgumentException.class, () -> calculator.recalculate(recipe));
    }

    @Test
    void standardFoodIdOverridesClientNameAndKeepsRecognitionMapping() {
        FoodDatabase genericFish = new FoodDatabase();
        genericFish.setId(42L);
        genericFish.setFoodName("鱼肉（熟，通用）");
        genericFish.setEnabled(true);
        genericFish.setProtein(new BigDecimal("22"));
        genericFish.setCarb(BigDecimal.ZERO);
        genericFish.setFat(new BigDecimal("6"));
        genericFish.setCalorie(new BigDecimal("145"));
        when(foods.findAllById(List.of(42L))).thenReturn(List.of(genericFish));
        Ingredient ingredient = ingredient("客户端伪造名称", "100");
        ingredient.setRecognizedName("清蒸鱼");
        ingredient.setFoodDatabaseId(42L);
        ingredient.setMappingSource("AI_USER_SELECTED");
        Recipe recipe = new Recipe();
        recipe.setIngredients(List.of(ingredient));

        calculator.recalculate(recipe);

        assertEquals("鱼肉（熟，通用）", ingredient.getFoodName());
        assertEquals("清蒸鱼", ingredient.getRecognizedName());
        assertEquals(new BigDecimal("145.0"), ingredient.getCalorie());
    }

    private Ingredient ingredient(String name, String grams) {
        Ingredient ingredient = new Ingredient();
        ingredient.setFoodName(name);
        ingredient.setWeight(new BigDecimal(grams));
        ingredient.setProtein(BigDecimal.ZERO);
        ingredient.setCarb(BigDecimal.ZERO);
        ingredient.setFat(BigDecimal.ZERO);
        ingredient.setCalorie(BigDecimal.ZERO);
        return ingredient;
    }
}
