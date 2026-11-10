package com.fitness.service;

import com.fitness.dto.WorkoutEstimateRequest;
import com.fitness.entity.Exercise;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.User;
import com.fitness.entity.Workout;
import com.fitness.mapper.WorkoutMapper;
import com.fitness.repository.ExerciseDatabaseRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 从 Java 权威动作目录生成预计消耗；不接受客户端提交的热量结果。 */
@Service
public class WorkoutCalorieEstimationService {
    private final ExerciseDatabaseRepository exercises;
    private final UserService users;
    private final WorkoutMapper workouts;

    public WorkoutCalorieEstimationService(ExerciseDatabaseRepository exercises, UserService users,
                                           WorkoutMapper workouts) {
        this.exercises = exercises;
        this.users = users;
        this.workouts = workouts;
    }

    public Workout estimate(Long userId, WorkoutEstimateRequest request) {
        Workout workout = new Workout();
        workout.setUserId(userId);
        workout.setWorkoutId(request.workoutId());
        workout.setEstimatedDurationMinutes(request.estimatedDurationMinutes());
        workout.setExercises(request.exercises().stream().map(item -> {
            Exercise exercise = new Exercise();
            exercise.setName(item.name().trim());
            exercise.setWeight(item.weight());
            exercise.setSets(item.sets());
            exercise.setReps(item.reps());
            exercise.setCalorieDurationSeconds(item.calorieDurationSeconds());
            exercise.setCalorieBasisSets(item.calorieBasisSets());
            return exercise;
        }).toList());
        return estimate(userId, workout);
    }

    public Workout estimate(Long userId, Workout workout) {
        if (workout.getExercises() == null || workout.getExercises().isEmpty()) {
            throw new IllegalArgumentException("请至少添加一个训练动作");
        }
        Workout existing = workout.getWorkoutId() == null || workout.getWorkoutId().isBlank()
                ? null : workouts.findByUserIdAndWorkoutId(userId, workout.getWorkoutId());
        boolean serverPrepared = positive(workout.getCalorieReferenceWeight());
        int requestedDurationMinutes = firstPositive(
                serverPrepared ? workout.getEstimatedDurationMinutes()
                        : (existing == null ? null : existing.getEstimatedDurationMinutes()),
                serverPrepared ? null : workout.getEstimatedDurationMinutes(),
                WorkoutCalorieFormula.structuralDurationMinutes(
                        workout.getExercises().stream().map(Exercise::getSets).toList()));
        BigDecimal referenceWeight = serverPrepared
                ? workout.getCalorieReferenceWeight()
                : (existing != null && positive(existing.getCalorieReferenceWeight())
                ? existing.getCalorieReferenceWeight() : currentWeight(userId));

        Map<String, ExerciseDatabase> references = new LinkedHashMap<>();
        exercises.findByNameInAndEnabledTrue(workout.getExercises().stream()
                        .map(Exercise::getName).distinct().toList())
                .forEach(item -> references.put(item.getName(), item));
        List<BigDecimal> mets = workout.getExercises().stream().map(item -> {
            ExerciseDatabase reference = references.get(item.getName());
            if (reference == null) throw new IllegalArgumentException("动作库中不存在或已停用：" + item.getName());
            return reference.getMet();
        }).toList();

        List<Integer> durations = resolveDurationSeconds(workout.getExercises(), existing,
                requestedDurationMinutes, serverPrepared);
        BigDecimal total = BigDecimal.ZERO;
        for (int index = 0; index < workout.getExercises().size(); index++) {
            Exercise exercise = workout.getExercises().get(index);
            exercise.setCalorieDurationSeconds(durations.get(index));
            BigDecimal value = WorkoutCalorieFormula.calories(referenceWeight, mets.get(index), durations.get(index));
            exercise.setCalories(value);
            total = total.add(value);
        }
        int totalDurationSeconds = durations.stream().mapToInt(Integer::intValue).sum();
        if (totalDurationSeconds > 86400) throw new IllegalArgumentException("训练预计时长不能超过1440分钟");
        workout.setEstimatedDurationMinutes(Math.max(1, (int) Math.ceil(totalDurationSeconds / 60.0)));
        workout.setCalorieReferenceWeight(referenceWeight);
        workout.setEstimatedCalories(total.setScale(1, RoundingMode.HALF_UP));
        workout.setTotalCalories(workout.getEstimatedCalories());
        return workout;
    }

    /**
     * 已保存计划以数据库中的动作时长为权威；新增计划只在第一次按总时长分配。
     * 此后组数变化按本动作原比例调整，未修改动作的时长和热量保持不变。
     */
    private List<Integer> resolveDurationSeconds(List<Exercise> current, Workout existing,
                                                 int requestedDurationMinutes, boolean serverPrepared) {
        Map<String, ArrayDeque<Exercise>> storedByName = new LinkedHashMap<>();
        if (existing != null && existing.getExercises() != null) {
            existing.getExercises().forEach(item -> storedByName
                    .computeIfAbsent(item.getName(), ignored -> new ArrayDeque<>()).add(item));
        }

        boolean hasStoredActions = storedByName.values().stream().anyMatch(queue -> !queue.isEmpty());
        boolean hasPreparedDurations = serverPrepared && current.stream()
                .allMatch(item -> positive(item.getCalorieDurationSeconds()));
        boolean hasClientSnapshots = current.stream().allMatch(item ->
                positive(item.getCalorieDurationSeconds()) && positive(item.getCalorieBasisSets()));
        if (!hasStoredActions && !hasPreparedDurations && !hasClientSnapshots) {
            return WorkoutCalorieFormula.allocateDurationSeconds(requestedDurationMinutes,
                    current.stream().map(Exercise::getSets).toList());
        }

        List<Integer> result = new ArrayList<>();
        for (Exercise item : current) {
            ArrayDeque<Exercise> matches = storedByName.get(item.getName());
            Exercise stored = matches == null || matches.isEmpty() ? null : matches.removeFirst();
            if (stored != null && positive(stored.getCalorieDurationSeconds())) {
                result.add(WorkoutCalorieFormula.adjustDurationSeconds(
                        stored.getCalorieDurationSeconds(), stored.getSets(), item.getSets()));
            } else if (positive(item.getCalorieDurationSeconds())) {
                int basisSets = positive(item.getCalorieBasisSets()) ? item.getCalorieBasisSets() : item.getSets();
                result.add(WorkoutCalorieFormula.adjustDurationSeconds(
                        item.getCalorieDurationSeconds(), basisSets, item.getSets()));
            } else {
                result.add(WorkoutCalorieFormula.structuralDurationSeconds(item.getSets()));
            }
        }
        return List.copyOf(result);
    }

    private BigDecimal currentWeight(Long userId) {
        User user = users.findById(userId);
        if (user == null || user.getWeight() == null || user.getWeight().signum() <= 0) {
            throw new IllegalArgumentException("请先完善有效体重，才能估算训练消耗");
        }
        return user.getWeight();
    }

    private int firstPositive(Integer preferred, Integer existing, int fallback) {
        if (preferred != null && preferred > 0) return preferred;
        if (existing != null && existing > 0) return existing;
        return fallback;
    }

    private boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private boolean positive(Integer value) {
        return value != null && value > 0;
    }
}
