package com.fitness.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AnalysisSqlContractTest {
    @Test
    void analysisUsesNutritionSnapshotsAndCompletedActualCalories() throws IOException {
        String intake = resource("mapper/DailyIntakeMapper.xml");
        String workout = resource("mapper/WorkoutMapper.xml");
        String weight = resource("mapper/WeightRecordMapper.xml");

        assertTrue(intake.contains("SUM(calorie_snapshot)"));
        assertTrue(intake.contains("SUM(protein_snapshot)"));
        assertTrue(intake.contains("SUM(carb_snapshot)"));
        assertTrue(intake.contains("SUM(fat_snapshot)"));
        assertTrue(workout.contains("WHEN status = 'COMPLETED' THEN actual_calories ELSE 0"));
        assertTrue(weight.contains("MAX(id) AS latest_id"));
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("missing test resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
