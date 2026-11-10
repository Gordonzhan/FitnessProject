package com.fitness.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 原子累计 AI 未匹配名称，供后续管理员审核，绝不直接写入正式食材库。 */
@Mapper
public interface FoodDatabaseCandidateMapper {
    void observe(@Param("recognizedName") String recognizedName,
                 @Param("normalizedName") String normalizedName,
                 @Param("cookingMethod") String cookingMethod);
}
