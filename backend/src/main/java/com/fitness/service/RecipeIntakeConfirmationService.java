package com.fitness.service;

import com.fitness.entity.Recipe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

/** 在同一数据库事务中保存菜谱并写入饮食快照，避免只完成其中一半。 */
@Service
public class RecipeIntakeConfirmationService {
    private final RecipeService recipeService;
    private final CompareService compareService;

    public RecipeIntakeConfirmationService(RecipeService recipeService, CompareService compareService) {
        this.recipeService = recipeService;
        this.compareService = compareService;
    }

    @Transactional(rollbackFor = Exception.class)
    public Recipe saveAndAddToDailyIntake(Recipe recipe, LocalDate date, String requestId,
                                           String mealSlot, BigDecimal servingCount,
                                           LocalTime consumedTime) {
        Recipe saved = recipeService.saveRecipe(recipe);
        compareService.addRecipeToDailyIntake(recipe.getUserId(), date, saved.getRecipeId(), requestId,
                mealSlot, servingCount, consumedTime);
        return saved;
    }
}
