package com.fitness.mapper;

import com.fitness.entity.Exercise;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ExerciseMapper {
    List<Exercise> findByWorkoutDbId(@Param("workoutDbId") Long workoutDbId);
    List<Exercise> findByWorkoutDbIds(@Param("workoutDbIds") List<Long> workoutDbIds);
    /** 按完成时间倒序返回指定动作的历史负重，调用方取每个动作第一条作为最近值。 */
    List<Exercise> findCompletedLoadsByNames(@Param("userId") Long userId,
                                             @Param("names") List<String> names);
    void insert(Exercise exercise);
    void update(Exercise exercise);
    void deleteByWorkoutDbId(@Param("workoutDbId") Long workoutDbId);
}
