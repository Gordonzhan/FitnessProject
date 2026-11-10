package com.fitness.service;

import com.fitness.common.PageResult;
import com.fitness.entity.ExerciseDatabase;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentReadToolServiceTest {
    private final UserService users = mock(UserService.class);
    private final TrainingPreferenceService preferences = mock(TrainingPreferenceService.class);
    private final AnalysisService analysis = mock(AnalysisService.class);
    private final WorkoutService workouts = mock(WorkoutService.class);
    private final DatabaseService database = mock(DatabaseService.class);
    private final AgentReadToolService service = new AgentReadToolService(
            users, preferences, analysis, workouts, database, "Asia/Hong_Kong");

    @Test
    @SuppressWarnings("unchecked")
    void exerciseSearchReturnsGuidanceLimitationsAndAlternativesFromAuthoritativeCatalog() {
        ExerciseDatabase exercise = new ExerciseDatabase();
        exercise.setExerciseCode("BODYWEIGHT_SQUAT");
        exercise.setName("徒手深蹲");
        exercise.setCategory("力量训练");
        exercise.setPrimaryMuscles("股四头肌,臀大肌");
        exercise.setEquipment("无");
        exercise.setIntensity("中等");
        exercise.setInstructions("髋膝协同屈曲，稳定起身");
        exercise.setSafetyCues("膝盖沿脚尖方向移动");
        exercise.setCommonMistakes("膝内扣,腰背失去控制");
        exercise.setLimitationTags("KNEE_PAIN,LOW_BACK_PAIN,KNEE_PAIN");
        exercise.setAlternativeExerciseCodes("CHAIR_SQUAT,LEG_PRESS");
        exercise.setDefaultSets(4);
        exercise.setDefaultReps(12);
        exercise.setDefaultRestSeconds(60);
        exercise.setDefaultRpe(7);
        exercise.setTempo("3-1-1");
        when(database.searchExercises("深蹲", "", 0, 3))
                .thenReturn(new PageResult<>(List.of(exercise), 1, 0, 3));

        Map<String, Object> response = service.searchExercises("深蹲", 3);

        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        assertEquals(1, items.size());
        Map<String, Object> item = items.get(0);
        assertEquals("髋膝协同屈曲，稳定起身", item.get("instructions"));
        assertEquals("膝盖沿脚尖方向移动", item.get("safetyCues"));
        assertEquals("膝内扣,腰背失去控制", item.get("commonMistakes"));
        assertEquals(List.of("KNEE_PAIN", "LOW_BACK_PAIN"), item.get("limitationTags"));
        assertEquals(List.of("CHAIR_SQUAT", "LEG_PRESS"), item.get("alternativeExerciseCodes"));
        assertEquals(4, item.get("defaultSets"));
        assertEquals(7, item.get("defaultRpe"));
        verify(database).searchExercises("深蹲", "", 0, 3);
    }
}
