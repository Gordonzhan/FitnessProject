package com.fitness.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActivitySqlContractTest {
    @Test
    void activityRecordsAreIdempotentAndNeverWriteWorkoutCalories() throws IOException {
        String mapper = resource("mapper/ActivityDailyRecordMapper.xml");
        assertTrue(mapper.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(mapper.contains("user_id = #{userId} AND source = #{source}"));
        assertFalse(mapper.contains("workouts"));
        assertFalse(mapper.contains("actual_calories"));
    }

    private String resource(String name) throws IOException {
        try (var stream = getClass().getClassLoader().getResourceAsStream(name)) {
            if (stream == null) throw new IOException("missing test resource: " + name);
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
