package com.fitness.mapper;

import com.fitness.entity.Workout;
import com.fitness.dto.DailyWorkoutSummary;
import com.fitness.dto.ComparisonParticipantStats;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

@Mapper
public interface WorkoutMapper {
    List<Workout> findByUserId(@Param("userId") Long userId);
    Workout findById(@Param("id") Long id);
    Workout findByUserIdAndWorkoutId(@Param("userId") Long userId, @Param("workoutId") String workoutId);
    Workout findByDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    Workout findCompletedByDate(@Param("userId") Long userId, @Param("date") LocalDate date);
    List<DailyWorkoutSummary> aggregateByDateRange(@Param("userId") Long userId,
                                                   @Param("startDate") LocalDate startDate,
                                                   @Param("endDate") LocalDate endDate);
    ComparisonParticipantStats aggregateComparisonStats(@Param("userId") Long userId,
                                                         @Param("startDate") LocalDate startDate,
                                                         @Param("endDate") LocalDate endDate);
    void insert(Workout workout);
    void update(Workout workout);
    int updateState(Workout workout);
    void delete(@Param("id") Long id);
}
