package com.fitness.mapper;

import com.fitness.entity.Recipe;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface RecipeMapper {
    List<Recipe> findAll();
    List<Recipe> findByUserId(@Param("userId") Long userId);
    long countByUserIdAndKeyword(@Param("userId") Long userId, @Param("keyword") String keyword);
    List<Recipe> searchByUserId(@Param("userId") Long userId, @Param("keyword") String keyword,
                                @Param("offset") int offset, @Param("size") int size);
    Recipe findById(@Param("id") Long id);
    Recipe findByUserIdAndRecipeId(@Param("userId") Long userId, @Param("recipeId") String recipeId);
    void insert(Recipe recipe);
    void update(Recipe recipe);
    void delete(@Param("id") Long id);
}
