package com.fitness.service;

import com.fitness.dto.TrainingPreferenceRequest;
import com.fitness.dto.TrainingPreferenceResponse;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.UserTrainingPreference;
import com.fitness.mapper.UserTrainingPreferenceMapper;
import com.fitness.repository.ExerciseDatabaseRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrainingPreferenceServiceTest {
    @Mock
    private UserTrainingPreferenceMapper mapper;
    @Mock
    private ExerciseDatabaseRepository exerciseRepository;

    @Test
    void missingPreferenceReturnsSafeDefaultsWithoutWriting() {
        TrainingPreferenceService service = new TrainingPreferenceService(mapper, exerciseRepository);

        TrainingPreferenceResponse response = service.get(7L);

        assertEquals("BEGINNER", response.experienceLevel());
        assertEquals(List.of(), response.equipmentTags());
        assertEquals(5, response.targetRpeMin());
        verify(mapper, never()).upsert(any());
    }

    @Test
    void saveNormalizesListsAndKeepsUserIdFromAuthContext() {
        TrainingPreferenceService service = new TrainingPreferenceService(mapper, exerciseRepository);
        ExerciseDatabase pushUp = new ExerciseDatabase();
        pushUp.setExerciseCode("EX_PUSH_UP");
        when(exerciseRepository.findByExerciseCodeInAndEnabledTrue(anyList())).thenReturn(List.of(pushUp));
        when(mapper.findByUserId(9L)).thenAnswer(invocation -> storedPreference(9L));
        TrainingPreferenceRequest request = new TrainingPreferenceRequest(
                "INTERMEDIATE", 4, 60, "GYM", "STRENGTH",
                List.of("哑铃", " 哑铃 "), List.of("SHOULDER"),
                List.of("ex_push_up"), List.of(), 6, 8);

        service.save(9L, request);

        ArgumentCaptor<UserTrainingPreference> saved = ArgumentCaptor.forClass(UserTrainingPreference.class);
        verify(mapper).upsert(saved.capture());
        assertEquals(9L, saved.getValue().getUserId());
        assertEquals("哑铃", saved.getValue().getEquipmentTags());
        assertEquals("EX_PUSH_UP", saved.getValue().getPreferredExerciseCodes());
    }

    @Test
    void saveRejectsInvalidRpeAndOverlappingExerciseLists() {
        TrainingPreferenceService service = new TrainingPreferenceService(mapper, exerciseRepository);
        TrainingPreferenceRequest invalidRpe = request(List.of(), List.of(), 8, 5);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, invalidRpe));

        TrainingPreferenceRequest overlap = request(List.of("EX_SQUAT"), List.of("EX_SQUAT"), 5, 8);
        assertThrows(IllegalArgumentException.class, () -> service.save(1L, overlap));
        verify(mapper, never()).upsert(any());
    }

    @Test
    void saveRejectsUnknownExerciseCode() {
        TrainingPreferenceService service = new TrainingPreferenceService(mapper, exerciseRepository);
        when(exerciseRepository.findByExerciseCodeInAndEnabledTrue(anyList())).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class,
                () -> service.save(1L, request(List.of("EX_UNKNOWN"), List.of(), 5, 8)));
        verify(mapper, never()).upsert(any());
    }

    private TrainingPreferenceRequest request(List<String> preferred, List<String> excluded, int min, int max) {
        return new TrainingPreferenceRequest("BEGINNER", 3, 45, "HOME", "GENERAL_FITNESS",
                List.of("无"), List.of(), preferred, excluded, min, max);
    }

    private UserTrainingPreference storedPreference(Long userId) {
        UserTrainingPreference value = new UserTrainingPreference();
        value.setUserId(userId);
        value.setExperienceLevel("INTERMEDIATE");
        value.setWeeklyDays(4);
        value.setSessionMinutes(60);
        value.setTrainingLocation("GYM");
        value.setTrainingGoal("STRENGTH");
        value.setEquipmentTags("哑铃");
        value.setLimitationTags("SHOULDER");
        value.setPreferredExerciseCodes("EX_PUSH_UP");
        value.setTargetRpeMin(6);
        value.setTargetRpeMax(8);
        return value;
    }
}
