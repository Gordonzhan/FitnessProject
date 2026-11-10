package com.fitness.service;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkoutCalorieFormulaTest {
    @Test
    void fullSessionDurationIsAllocatedAcrossExercises() {
        List<BigDecimal> result = WorkoutCalorieFormula.allocate(new BigDecimal("68"), 60, List.of(
                new WorkoutCalorieFormula.Input(new BigDecimal("5"), 4),
                new WorkoutCalorieFormula.Input(new BigDecimal("6"), 4),
                new WorkoutCalorieFormula.Input(new BigDecimal("6"), 4),
                new WorkoutCalorieFormula.Input(new BigDecimal("5"), 4)));

        assertEquals(List.of(new BigDecimal("85.0"), new BigDecimal("102.0"),
                new BigDecimal("102.0"), new BigDecimal("85.0")), result);
        assertEquals(new BigDecimal("374.0"), result.stream().reduce(BigDecimal.ZERO, BigDecimal::add));
    }

    @Test
    void structuralDurationCountsWorkAndOnlyBetweenSetRest() {
        assertEquals(10, WorkoutCalorieFormula.structuralDurationMinutes(List.of(4, 4)));
    }

    @Test
    void setChangeScalesOnlyTheProvidedExerciseDuration() {
        assertEquals(390, WorkoutCalorieFormula.structuralDurationSeconds(5));
        assertEquals(1300, WorkoutCalorieFormula.adjustDurationSeconds(1000, 4, 5));
    }

    @Test
    void invalidWeightIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> WorkoutCalorieFormula.allocate(
                BigDecimal.ZERO, 60,
                List.of(new WorkoutCalorieFormula.Input(new BigDecimal("5"), 4))));
    }
}
