package com.fitness.mapper;

import com.fitness.entity.SystemRecipeTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemRecipeTemplateMapper {
    long countEnabled(@Param("keyword") String keyword, @Param("goal") String goal,
                      @Param("cuisine") String cuisine);
    List<SystemRecipeTemplate> searchEnabled(@Param("keyword") String keyword, @Param("goal") String goal,
                                             @Param("cuisine") String cuisine,
                                             @Param("offset") int offset,
                                             @Param("size") int size);
    SystemRecipeTemplate findByRecipeId(@Param("recipeId") String recipeId);
    long countEnabledByCoverImageUrl(@Param("imageUrl") String imageUrl);
}
