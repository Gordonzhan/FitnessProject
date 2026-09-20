package com.fitness.mapper;

import com.fitness.entity.Ingredient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface IngredientMapper {
    void insert(Ingredient ingredient);
    void update(Ingredient ingredient);
    void deleteByRecipeDbId(@Param("recipeDbId") Long recipeDbId);
    List<Ingredient> findByRecipeDbId(@Param("recipeDbId") Long recipeDbId);
    List<Ingredient> findByRecipeDbIds(@Param("recipeDbIds") List<Long> recipeDbIds);
}
