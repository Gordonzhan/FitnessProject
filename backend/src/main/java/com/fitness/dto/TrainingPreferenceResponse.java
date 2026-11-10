package com.fitness.dto;

import java.time.LocalDateTime;
import java.util.List;

public record TrainingPreferenceResponse(
        String experienceLevel,
        Integer weeklyDays,
        Integer sessionMinutes,
        String trainingLocation,
        String trainingGoal,
        List<String> equipmentTags,
        List<String> limitationTags,
        List<String> preferredExerciseCodes,
        List<String> excludedExerciseCodes,
        Integer targetRpeMin,
        Integer targetRpeMax,
        LocalDateTime updatedAt) {}
