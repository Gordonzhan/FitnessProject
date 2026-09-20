package com.fitness.mapper;

import com.fitness.entity.DailyIntake;
import com.fitness.dto.DailyNutritionSummary;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

@Mapper
public interface DailyIntakeMapper {
    List<DailyIntake> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    int insertIfAbsent(DailyIntake dailyIntake);
    BigDecimal sumCaloriesByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    List<DailyNutritionSummary> aggregateByDateRange(@Param("userId") Long userId,
                                                     @Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
    int deleteByUserIdAndDateAndId(@Param("userId") Long userId,
                                 @Param("date") LocalDate date,
                                 @Param("intakeId") Long intakeId);
    void deleteByUserIdAndDateAndRecipeDbId(@Param("userId") Long userId,
                                            @Param("date") LocalDate date,
                                            @Param("recipeDbId") Long recipeDbId);
    void deleteByUserIdAndDateAndRecipeBusinessId(@Param("userId") Long userId,
                                                  @Param("date") LocalDate date,
                                                  @Param("recipeId") String recipeId);
}
