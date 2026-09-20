package com.fitness.service;

import com.fitness.entity.Workout;
import com.fitness.entity.Exercise;

import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.List;

public interface WorkoutService {
    /** 查询指定用户的全部训练记录，并装配动作明细。 */
    List<Workout> getWorkoutsByUserId(Long userId);
    /** 查询用户在指定日期的训练记录。 */
    Workout getWorkoutByDate(Long userId, LocalDate date);
    /** 新建或更新训练记录及其动作明细。 */
    Workout saveWorkout(Workout workout);
    /** 将到期训练计划确认完成，并记录实际热量消耗。 */
    Workout completeWorkout(Long userId, String workoutId, BigDecimal actualCalories);
    /** 取消尚未完成的训练计划，使其不再计入能量统计。 */
    Workout cancelWorkout(Long userId, String workoutId);
    /** 删除属于指定用户的训练记录及其动作明细。 */
    boolean deleteWorkoutByWorkoutId(Long userId, String workoutId);
}
