package com.fitness.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Java 传给 Python Agent 的最小训练上下文，不包含用户身份或数据库主键。 */
public record WorkoutDraftAgentRequest(
        String requestId,
        LocalDate date,
        String userRequest,
        TrainingIntent trainingIntent,
        Preferences preferences,
        List<Candidate> candidates,
        List<DraftExercise> currentExercises,
        String replaceExerciseCode,
        List<String> validationIssues,
        String mode) {

    public record Preferences(
            String experienceLevel,
            Integer sessionMinutes,
            String trainingLocation,
            String trainingGoal,
            List<String> equipmentTags,
            List<String> limitationTags,
            List<String> preferredExerciseCodes,
            List<String> excludedExerciseCodes,
            Integer targetRpeMin,
            Integer targetRpeMax) {}

    public record Candidate(
            String exerciseCode,
            String name,
            String category,
            String primaryMuscles,
            String primaryRegion,
            String equipment,
            String intensity,
            Integer defaultSets,
            Integer defaultReps,
            Integer defaultDurationSeconds,
            Integer defaultRestSeconds,
            Integer defaultRpe,
            String loadType,
            BigDecimal recentWeightKg) {}

    /** Java 从自然语言中提取的确定性训练约束，模型生成后仍按同一约束复核。 */
    public record TrainingIntent(
            Integer minimumDurationMinutes,
            Integer maximumDurationMinutes,
            List<FocusRequirement> focusRequirements,
            List<String> excludedRegions) {}

    public record FocusRequirement(
            String region,
            String label,
            Integer minimumExercises,
            Integer maximumExercises) {}

    public record DraftExercise(
            String exerciseCode,
            Integer sets,
            Integer reps,
            Integer durationSeconds,
            Integer restSeconds,
            Integer rpe,
            BigDecimal weight,
            String rationale) {}
}
