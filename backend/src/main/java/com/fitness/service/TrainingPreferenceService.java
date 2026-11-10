package com.fitness.service;

import com.fitness.dto.TrainingPreferenceRequest;
import com.fitness.dto.TrainingPreferenceResponse;
import com.fitness.entity.UserTrainingPreference;
import com.fitness.mapper.UserTrainingPreferenceMapper;
import com.fitness.repository.ExerciseDatabaseRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class TrainingPreferenceService {
    private final UserTrainingPreferenceMapper mapper;
    private final ExerciseDatabaseRepository exerciseRepository;

    public TrainingPreferenceService(UserTrainingPreferenceMapper mapper,
                                     ExerciseDatabaseRepository exerciseRepository) {
        this.mapper = mapper;
        this.exerciseRepository = exerciseRepository;
    }

    @Transactional(readOnly = true)
    public TrainingPreferenceResponse get(Long userId) {
        UserTrainingPreference stored = mapper.findByUserId(userId);
        return stored == null ? defaults() : toResponse(stored);
    }

    @Transactional(rollbackFor = Exception.class)
    public TrainingPreferenceResponse save(Long userId, TrainingPreferenceRequest request) {
        if (request.targetRpeMin() > request.targetRpeMax()) {
            throw new IllegalArgumentException("最低RPE不能高于最高RPE");
        }

        List<String> equipment = normalizeLabels(request.equipmentTags());
        List<String> limitations = normalizeCodes(request.limitationTags());
        List<String> preferred = normalizeCodes(request.preferredExerciseCodes());
        List<String> excluded = normalizeCodes(request.excludedExerciseCodes());
        Set<String> overlap = new LinkedHashSet<>(preferred);
        overlap.retainAll(excluded);
        if (!overlap.isEmpty()) throw new IllegalArgumentException("同一动作不能同时设为偏好和排除");
        validateExerciseCodes(preferred, excluded);

        UserTrainingPreference preference = new UserTrainingPreference();
        preference.setUserId(userId);
        preference.setExperienceLevel(request.experienceLevel());
        preference.setWeeklyDays(request.weeklyDays());
        preference.setSessionMinutes(request.sessionMinutes());
        preference.setTrainingLocation(request.trainingLocation());
        preference.setTrainingGoal(request.trainingGoal());
        preference.setEquipmentTags(join(equipment));
        preference.setLimitationTags(join(limitations));
        preference.setPreferredExerciseCodes(join(preferred));
        preference.setExcludedExerciseCodes(join(excluded));
        preference.setTargetRpeMin(request.targetRpeMin());
        preference.setTargetRpeMax(request.targetRpeMax());
        mapper.upsert(preference);
        return toResponse(mapper.findByUserId(userId));
    }

    private void validateExerciseCodes(List<String> preferred, List<String> excluded) {
        Set<String> requested = new LinkedHashSet<>(preferred);
        requested.addAll(excluded);
        if (requested.isEmpty()) return;
        long found = exerciseRepository.findByExerciseCodeInAndEnabledTrue(new ArrayList<>(requested)).stream()
                .map(item -> item.getExerciseCode().toUpperCase())
                .distinct()
                .count();
        if (found != requested.size()) throw new IllegalArgumentException("偏好或排除动作中包含不存在的动作");
    }

    private TrainingPreferenceResponse defaults() {
        return new TrainingPreferenceResponse("BEGINNER", 3, 45, "FLEXIBLE", "GENERAL_FITNESS",
                List.of(), List.of(), List.of(), List.of(), 5, 7, null);
    }

    private TrainingPreferenceResponse toResponse(UserTrainingPreference value) {
        return new TrainingPreferenceResponse(value.getExperienceLevel(), value.getWeeklyDays(),
                value.getSessionMinutes(), value.getTrainingLocation(), value.getTrainingGoal(),
                split(value.getEquipmentTags()), split(value.getLimitationTags()),
                split(value.getPreferredExerciseCodes()), split(value.getExcludedExerciseCodes()),
                value.getTargetRpeMin(), value.getTargetRpeMax(), value.getUpdatedAt());
    }

    private List<String> normalizeLabels(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .filter(value -> !value.contains(","))
                .distinct().toList();
    }

    private List<String> normalizeCodes(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase()).distinct().toList();
    }

    private String join(List<String> values) {
        return values.isEmpty() ? null : String.join(",", values);
    }

    private List<String> split(String value) {
        if (value == null || value.isBlank()) return List.of();
        return List.of(value.split(","));
    }
}
