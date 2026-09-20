package com.fitness.mapper;

import com.fitness.entity.Exercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExerciseMapper {
    List<Exercise> findByWorkoutDbId(@Param("workoutDbId") Long workoutDbId);
    List<Exercise> findByWorkoutDbIds(@Param("workoutDbIds") List<Long> workoutDbIds);
    void insert(Exercise exercise);
    void update(Exercise exercise);
    void deleteByWorkoutDbId(@Param("workoutDbId") Long workoutDbId);
}
