package com.fitness.service;

import com.fitness.dto.WorkoutEstimateRequest;
import com.fitness.entity.Exercise;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.Workout;
import com.fitness.mapper.WorkoutMapper;
import com.fitness.repository.ExerciseDatabaseRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutCalorieEstimationServiceTest {
    @Mock ExerciseDatabaseRepository exercises;
    @Mock UserService users;
    @Mock WorkoutMapper workouts;
    private WorkoutCalorieEstimationService service;

    @BeforeEach
    void setUp() {
        service = new WorkoutCalorieEstimationService(exercises, users, workouts);
    }

    @Test
    void existingPlanKeepsItsDurationAndWeightSnapshots() {
        Workout existing = new Workout();
        existing.setEstimatedDurationMinutes(60);
        existing.setCalorieReferenceWeight(new BigDecimal("68"));
        existing.setExercises(List.of(stored("深蹲", 4, 1800), stored("硬拉", 4, 1800)));
        when(workouts.findByUserIdAndWorkoutId(7L, "ai-plan")).thenReturn(existing);
        when(exercises.findByNameInAndEnabledTrue(List.of("深蹲", "硬拉")))
                .thenReturn(List.of(reference("深蹲", "5"), reference("硬拉", "6")));

        Workout result = service.estimate(7L, new WorkoutEstimateRequest("ai-plan", 999, List.of(
                new WorkoutEstimateRequest.ExerciseInput("深蹲", new BigDecimal("90"), 4, 10),
                new WorkoutEstimateRequest.ExerciseInput("硬拉", new BigDecimal("80"), 4, 10))));

        assertEquals(60, result.getEstimatedDurationMinutes());
        assertEquals(new BigDecimal("68"), result.getCalorieReferenceWeight());
        assertEquals(List.of(new BigDecimal("170.0"), new BigDecimal("204.0")),
                result.getExercises().stream().map(item -> item.getCalories()).toList());
        assertEquals(new BigDecimal("374.0"), result.getEstimatedCalories());
        verify(users, never()).findById(7L);
    }

    @Test
    void changingOneExerciseSetsDoesNotChangeAnotherExerciseCalories() {
        Workout existing = new Workout();
        existing.setEstimatedDurationMinutes(60);
        existing.setCalorieReferenceWeight(new BigDecimal("68"));
        existing.setExercises(List.of(stored("杠铃划船", 4, 784), stored("器械坐姿划船", 4, 784)));
        when(workouts.findByUserIdAndWorkoutId(7L, "row-plan")).thenReturn(existing);
        when(exercises.findByNameInAndEnabledTrue(List.of("杠铃划船", "器械坐姿划船")))
                .thenReturn(List.of(reference("杠铃划船", "5"), reference("器械坐姿划船", "5")));

        Workout result = service.estimate(7L, new WorkoutEstimateRequest("row-plan", 60, List.of(
                new WorkoutEstimateRequest.ExerciseInput("杠铃划船", new BigDecimal("45"), 5, 10),
                new WorkoutEstimateRequest.ExerciseInput("器械坐姿划船", new BigDecimal("35"), 4, 10))));

        assertEquals(1019, result.getExercises().get(0).getCalorieDurationSeconds());
        assertEquals(784, result.getExercises().get(1).getCalorieDurationSeconds());
        assertEquals(new BigDecimal("74.0"), result.getExercises().get(1).getCalories());
    }

    private ExerciseDatabase reference(String name, String met) {
        ExerciseDatabase reference = new ExerciseDatabase();
        reference.setName(name);
        reference.setMet(new BigDecimal(met));
        return reference;
    }

    private Exercise stored(String name, int sets, int durationSeconds) {
        Exercise exercise = new Exercise();
        exercise.setName(name);
        exercise.setSets(sets);
        exercise.setReps(10);
        exercise.setCalorieDurationSeconds(durationSeconds);
        return exercise;
    }
}
