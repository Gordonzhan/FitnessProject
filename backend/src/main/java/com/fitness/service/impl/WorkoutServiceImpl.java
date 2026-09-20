package com.fitness.service.impl;

import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.entity.Workout;
import com.fitness.entity.Exercise;
import com.fitness.entity.WorkoutStatus;
import com.fitness.mapper.WorkoutMapper;
import com.fitness.mapper.ExerciseMapper;
import com.fitness.service.WorkoutService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class WorkoutServiceImpl implements WorkoutService {
    @Autowired
    private WorkoutMapper workoutMapper;
    @Autowired
    private ExerciseMapper exerciseMapper;
    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone;
    
    @Override
    public List<Workout> getWorkoutsByUserId(Long userId) {
        List<Workout> workouts = workoutMapper.findByUserId(userId);
        if (workouts.isEmpty()) return workouts;

        List<Long> ids = workouts.stream().map(Workout::getId).toList();
        Map<Long, List<Exercise>> exercisesByWorkout = exerciseMapper.findByWorkoutDbIds(ids).stream()
                .collect(Collectors.groupingBy(Exercise::getWorkoutDbId));
        workouts.forEach(workout -> workout.setExercises(
                exercisesByWorkout.getOrDefault(workout.getId(), List.of())));
        return workouts;
    }

    @Override
    public Workout getWorkoutByDate(Long userId, LocalDate date) {
        return workoutMapper.findByDate(userId, date);
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Workout saveWorkout(Workout workout) {
        prepareForSave(workout);
        // workoutId 是公开业务标识，id 仅作为数据库内部主键使用。
        if (workout.getWorkoutId() == null || workout.getWorkoutId().isBlank()) {
            workout.setWorkoutId(UUID.randomUUID().toString());
        }
        Workout existing = workoutMapper.findByUserIdAndWorkoutId(
                workout.getUserId(), workout.getWorkoutId());
        if (existing != null) {
            if (existing.getStatus() == WorkoutStatus.COMPLETED
                    && workout.getStatus() == WorkoutStatus.PLANNED) {
                throw new BusinessException(ApiErrorCode.CONFLICT, "已完成训练不能改回计划");
            }
            workout.setId(existing.getId());
            workoutMapper.update(workout);
        } else {
            workout.setId(null);
            // 同一业务 ID 或同一用户同一天的并发保存由唯一键定位到同一父记录。
            workoutMapper.insert(workout);
            Workout persisted = workoutMapper.findByUserIdAndWorkoutId(
                    workout.getUserId(), workout.getWorkoutId());
            if (persisted == null) throw new IllegalStateException("训练保存后无法读取");
            workout.setId(persisted.getId());
        }
        exerciseMapper.deleteByWorkoutDbId(workout.getId());
        
        // 处理关联的训练动作
        if (workout.getExercises() != null) {
            for (Exercise exercise : workout.getExercises()) {
                exercise.setWorkoutDbId(workout.getId());
                exerciseMapper.insert(exercise);
            }
        }
        
        return workout;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Workout completeWorkout(Long userId, String workoutId, BigDecimal actualCalories) {
        if (actualCalories == null || actualCalories.signum() < 0) {
            throw new IllegalArgumentException("请提供有效的实际训练消耗");
        }
        Workout workout = requiredWorkout(userId, workoutId);
        if (workout.getStatus() == WorkoutStatus.COMPLETED) return workout;
        if (workout.getStatus() == WorkoutStatus.CANCELLED) {
            throw new BusinessException(ApiErrorCode.CONFLICT, "已取消计划不能直接确认完成");
        }
        if (workout.getDate().isAfter(today())) {
            throw new BusinessException(ApiErrorCode.CONFLICT, "未来训练计划需到训练日后才能确认完成");
        }
        workout.setStatus(WorkoutStatus.COMPLETED);
        workout.setEstimatedCalories(defaultCalories(workout.getEstimatedCalories(), workout.getTotalCalories()));
        workout.setActualCalories(actualCalories);
        workout.setTotalCalories(actualCalories);
        workoutMapper.updateState(workout);
        return workout;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Workout cancelWorkout(Long userId, String workoutId) {
        Workout workout = requiredWorkout(userId, workoutId);
        if (workout.getStatus() == WorkoutStatus.CANCELLED) return workout;
        if (workout.getStatus() == WorkoutStatus.COMPLETED) {
            throw new BusinessException(ApiErrorCode.CONFLICT, "已完成训练不能取消");
        }
        workout.setStatus(WorkoutStatus.CANCELLED);
        workout.setEstimatedCalories(defaultCalories(workout.getEstimatedCalories(), workout.getTotalCalories()));
        workout.setActualCalories(null);
        workout.setTotalCalories(workout.getEstimatedCalories());
        workoutMapper.updateState(workout);
        return workout;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteWorkoutByWorkoutId(Long userId, String workoutId) {
        Workout workout = workoutMapper.findByUserIdAndWorkoutId(userId, workoutId);
        if (workout == null) {
            return false;
        }

        Long workoutDbId = workout.getId();
        // 删除关联的训练动作
        exerciseMapper.deleteByWorkoutDbId(workoutDbId);
        // 删除健身记录
        workoutMapper.delete(workoutDbId);
        return true;
    }

    /** 根据训练日期校验状态流转，并规范预计、实际及兼容热量字段。 */
    private void prepareForSave(Workout workout) {
        if (workout.getDate() == null) throw new IllegalArgumentException("请选择训练日期");
        LocalDate today = today();
        if (workout.getDate().isAfter(today.plusDays(90))) {
            throw new IllegalArgumentException("训练计划最多可安排未来90天");
        }
        WorkoutStatus status = workout.getStatus();
        if (status == null) {
            status = workout.getDate().isAfter(today) ? WorkoutStatus.PLANNED : WorkoutStatus.COMPLETED;
        }
        if (status == WorkoutStatus.CANCELLED) {
            throw new IllegalArgumentException("请通过取消计划操作变更状态");
        }
        if (workout.getDate().isAfter(today) && status != WorkoutStatus.PLANNED) {
            throw new IllegalArgumentException("未来日期只能保存训练计划");
        }
        if (workout.getDate().isBefore(today) && status == WorkoutStatus.PLANNED) {
            throw new IllegalArgumentException("历史日期只能补录已完成训练");
        }

        BigDecimal estimate = defaultCalories(workout.getEstimatedCalories(), workout.getTotalCalories());
        if (estimate == null || estimate.signum() < 0) {
            throw new IllegalArgumentException("请提供有效的预计训练消耗");
        }
        workout.setStatus(status);
        workout.setEstimatedCalories(estimate);
        if (status == WorkoutStatus.COMPLETED) {
            BigDecimal actual = defaultCalories(workout.getActualCalories(), workout.getTotalCalories());
            workout.setActualCalories(actual);
            workout.setTotalCalories(actual);
        } else {
            workout.setActualCalories(null);
            workout.setTotalCalories(estimate);
        }
    }

    /** 查询属于指定用户的训练；不存在时抛出统一的未找到异常。 */
    private Workout requiredWorkout(Long userId, String workoutId) {
        Workout workout = workoutMapper.findByUserIdAndWorkoutId(userId, workoutId);
        if (workout == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "训练记录不存在");
        }
        return workout;
    }

    /** 优先返回新字段热量，为空时回退到旧版兼容字段。 */
    private BigDecimal defaultCalories(BigDecimal preferred, BigDecimal fallback) {
        return preferred == null ? fallback : preferred;
    }

    /** 按配置的业务时区获取当前日期，避免服务器时区影响训练状态。 */
    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessZone));
    }
}
