package com.fitness.dto;

import com.fitness.entity.Ingredient;
import com.fitness.entity.RecipeImage;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class RecipeCatalogItem {
    private String recipeId;
    private String mealType;
    private String steps;
    private BigDecimal protein;
    private BigDecimal carb;
    private BigDecimal fat;
    private BigDecimal calorie;
    private List<RecipeImage> images;
    private List<Ingredient> ingredients;
    private String sourceType;
    private boolean editable;
    private boolean deletable;
    private String category;
    private String cuisineType;
    private String goalTags;
    private String servingDescription;
    private String allergenInfo;
    private String sourceName;
    private String sourceRef;
    private String dataVersion;
}
