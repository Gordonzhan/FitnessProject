package com.fitness.mapper;

import com.fitness.entity.SystemRecipeIngredient;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SystemRecipeIngredientMapper {
    List<SystemRecipeIngredient> findByTemplateDbId(@Param("templateDbId") Long templateDbId);
    List<SystemRecipeIngredient> findByTemplateDbIds(@Param("templateDbIds") List<Long> templateDbIds);
}
