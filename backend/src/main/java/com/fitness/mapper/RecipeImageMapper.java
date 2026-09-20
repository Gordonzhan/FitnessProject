package com.fitness.mapper;

import com.fitness.entity.RecipeImage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecipeImageMapper {
    int countByImageUrl(@Param("imageUrl") String imageUrl);
    void insert(RecipeImage recipeImage);
    void update(RecipeImage recipeImage);
    void deleteByRecipeDbId(@Param("recipeDbId") Long recipeDbId);
    List<RecipeImage> findByRecipeDbId(@Param("recipeDbId") Long recipeDbId);
    List<RecipeImage> findByRecipeDbIds(@Param("recipeDbIds") List<Long> recipeDbIds);
}
