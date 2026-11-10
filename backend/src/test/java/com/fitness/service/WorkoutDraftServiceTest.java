package com.fitness.service;

import com.fitness.ai.AgentClient;
import com.fitness.common.BusinessException;
import com.fitness.dto.TrainingPreferenceResponse;
import com.fitness.dto.WorkoutDraftAgentRequest;
import com.fitness.dto.WorkoutDraftResponse;
import com.fitness.entity.Exercise;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.User;
import com.fitness.entity.Workout;
import com.fitness.entity.WorkoutStatus;
import com.fitness.repository.ExerciseDatabaseRepository;
import com.fitness.mapper.ExerciseMapper;
import com.fitness.mapper.SystemRecipeTemplateMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WorkoutDraftServiceTest {
    @Mock AgentClient agentClient;
    @Mock TrainingPreferenceService preferenceService;
    @Mock ExerciseDatabaseRepository exerciseRepository;
    @Mock ExerciseMapper exerciseMapper;
    @Mock WorkoutService workoutService;
    @Mock UserService userService;
    @Mock CompareService compareService;
    @Mock SystemRecipeTemplateMapper recipeTemplateMapper;
    private WorkoutDraftService service;
    private LocalDate today;

    @BeforeEach
    void setUp() {
        service = new WorkoutDraftService(agentClient, preferenceService, exerciseRepository,
                exerciseMapper, workoutService, userService, compareService, recipeTemplateMapper, "Asia/Hong_Kong");
        today = LocalDate.now(ZoneId.of("Asia/Hong_Kong"));
    }

    @Test
    void createSendsOnlyEligibleCandidatesAndNormalizesDatabaseFields() {
        stubContext();
        when(agentClient.createWorkoutDraft(any(), eq("req-1")))
                .thenReturn(agentDraft("req-1", "EX_ONE", "EX_TWO"));

        WorkoutDraftResponse result = service.create(7L, today, "今天练胸和背，45分钟", null, "req-1");

        ArgumentCaptor<WorkoutDraftAgentRequest> request = ArgumentCaptor.forClass(WorkoutDraftAgentRequest.class);
        verify(agentClient).createWorkoutDraft(request.capture(), eq("req-1"));
        assertEquals(List.of("EX_ONE", "EX_TWO", "EX_DUMBBELL"),
                request.getValue().candidates().stream().map(WorkoutDraftAgentRequest.Candidate::exerciseCode).toList());
        assertEquals(45, request.getValue().preferences().sessionMinutes());
        assertEquals(36, request.getValue().trainingIntent().minimumDurationMinutes());
        assertEquals(45, result.estimatedDurationMinutes());
        assertEquals("动作一", result.exercises().get(0).name());
        assertEquals("无", result.exercises().get(0).equipment());
        assertEquals(4, result.exercises().get(0).sets());
        assertNotNull(result.exercises().get(0).calories());
        assertEquals(0, result.correctionCount());
        assertEquals("今天练胸和背，45分钟", request.getValue().userRequest());
        assertNotNull(result.nutritionPlan());
        assertEquals(new BigDecimal("1500"), result.nutritionPlan().remainingCalories());
        verify(workoutService, never()).saveWorkout(any());
    }

    @Test
    void createRejectsDateWithCompletedWorkoutBeforeCallingAgent() {
        Workout completed = new Workout();
        completed.setStatus(WorkoutStatus.COMPLETED);
        when(workoutService.getWorkoutByDate(7L, today)).thenReturn(completed);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.create(7L, today, "今天生成肩部训练", null, "req-completed-date"));

        assertTrue(error.getMessage().contains("训练已完成"));
        assertTrue(error.getMessage().contains("明天或其他未来日期"));
        verify(agentClient, never()).createWorkoutDraft(any(), any());
    }

    @Test
    void missingPreferenceUsesSimpleExercisesFromOnlyTheRequestedPrimaryRegion() {
        when(preferenceService.get(7L)).thenReturn(new TrainingPreferenceResponse(
                "BEGINNER", 3, 45, "FLEXIBLE", "GENERAL_FITNESS",
                List.of(), List.of(), List.of(), List.of(), 5, 7, null));
        ExerciseDatabase barbellChest = exercise("EX_BARBELL_CHEST", "杠铃卧推", "杠铃", null);
        barbellChest.setPrimaryMuscles("胸部");
        ExerciseDatabase back = exercise("EX_BACK", "徒手背部动作", "无", null);
        back.setPrimaryMuscles("背部");
        ExerciseDatabase bodyweightChest = exercise("EX_BODY_CHEST", "俯卧撑", "无", null);
        bodyweightChest.setPrimaryMuscles("胸部");
        ExerciseDatabase machineChest = exercise("EX_MACHINE_CHEST", "器械推胸", "推胸机", null);
        machineChest.setPrimaryMuscles("胸部");
        when(exerciseRepository.findByEnabledTrueAndFollowAlongEnabledTrueOrderBySortOrderDescNameAsc())
                .thenReturn(List.of(barbellChest, back, machineChest, bodyweightChest));
        stubUserAndNutrition();
        when(agentClient.createWorkoutDraft(any(), eq("req-defaults")))
                .thenReturn(agentDraft("req-defaults", "EX_BODY_CHEST", "EX_MACHINE_CHEST"));

        WorkoutDraftResponse result = service.create(7L, today, "今天练胸45分钟", null, "req-defaults");

        ArgumentCaptor<WorkoutDraftAgentRequest> request = ArgumentCaptor.forClass(WorkoutDraftAgentRequest.class);
        verify(agentClient).createWorkoutDraft(request.capture(), eq("req-defaults"));
        assertEquals(List.of("EX_BODY_CHEST", "EX_MACHINE_CHEST"),
                request.getValue().candidates().stream()
                        .filter(item -> "CHEST".equals(item.primaryRegion()))
                        .map(WorkoutDraftAgentRequest.Candidate::exerciseCode).toList());
        assertEquals(List.of("EX_BODY_CHEST", "EX_MACHINE_CHEST"), result.exercises().stream()
                .map(WorkoutDraftResponse.ExerciseDraft::exerciseCode).toList());
    }

    @Test
    void explicitExcludedRegionIsNotTreatedAsFocusAndIsRemovedBeforeModelCall() {
        when(preferenceService.get(7L)).thenReturn(new TrainingPreferenceResponse(
                "BEGINNER", 3, 60, "GYM", "GENERAL_FITNESS",
                List.of(), List.of(), List.of(), List.of(), 5, 7, LocalDateTime.now()));
        ExerciseDatabase lateralRaise = exercise("EX_LATERAL_RAISE", "哑铃侧平举", "哑铃", null);
        lateralRaise.setPrimaryMuscles("肩部");
        ExerciseDatabase shoulderPress = exercise("EX_SHOULDER_PRESS", "哑铃肩推", "哑铃", null);
        shoulderPress.setPrimaryMuscles("肩部");
        ExerciseDatabase benchPress = exercise("EX_BENCH_PRESS", "哑铃卧推", "哑铃", null);
        benchPress.setPrimaryMuscles("胸部");
        when(exerciseRepository.findByEnabledTrueAndFollowAlongEnabledTrueOrderBySortOrderDescNameAsc())
                .thenReturn(List.of(lateralRaise, shoulderPress, benchPress));
        stubUserAndNutrition();
        when(agentClient.createWorkoutDraft(any(), eq("req-excluded-region")))
                .thenReturn(agentDraft("req-excluded-region", "EX_LATERAL_RAISE", "EX_SHOULDER_PRESS"));

        WorkoutDraftResponse result = service.create(7L, today,
                "历史整理：生成胸部与肩部训练\n用户本轮明确要求：帮我生成一个练肩的训练计划，"
                        + "注意不要练胸的动作，并且给我三餐的饮食推荐",
                null, "req-excluded-region");

        ArgumentCaptor<WorkoutDraftAgentRequest> request = ArgumentCaptor.forClass(WorkoutDraftAgentRequest.class);
        verify(agentClient).createWorkoutDraft(request.capture(), eq("req-excluded-region"));
        assertEquals(List.of("SHOULDERS"), request.getValue().trainingIntent().focusRequirements().stream()
                .map(WorkoutDraftAgentRequest.FocusRequirement::region).toList());
        assertEquals(List.of("CHEST"), request.getValue().trainingIntent().excludedRegions());
        assertEquals(List.of("EX_LATERAL_RAISE", "EX_SHOULDER_PRESS"), request.getValue().candidates().stream()
                .map(WorkoutDraftAgentRequest.Candidate::exerciseCode).toList());
        assertEquals("肩部训练计划", result.title());
        assertTrue(result.exercises().stream().noneMatch(item -> item.primaryMuscles().contains("胸")));
    }

    @Test
    void conversationIntentUsesOnlyLatestUserTurnAndFoodNameCannotAddChestFocus() {
        when(preferenceService.get(7L)).thenReturn(new TrainingPreferenceResponse(
                "BEGINNER", 3, 60, "GYM", "GENERAL_FITNESS",
                List.of(), List.of(), List.of(), List.of(), 5, 7, LocalDateTime.now()));
        ExerciseDatabase pulldown = exercise("EX_PULLDOWN", "高位下拉", "绳索器械", null);
        pulldown.setPrimaryMuscles("背部,肱二头肌");
        ExerciseDatabase row = exercise("EX_ROW", "坐姿划船", "绳索器械", null);
        row.setPrimaryMuscles("背部,肱二头肌");
        ExerciseDatabase chestPress = exercise("EX_CHEST", "器械推胸", "推胸机", null);
        chestPress.setPrimaryMuscles("胸部,肱三头肌,肩部");
        when(exerciseRepository.findByEnabledTrueAndFollowAlongEnabledTrueOrderBySortOrderDescNameAsc())
                .thenReturn(List.of(chestPress, pulldown, row));
        stubUserAndNutrition();
        when(agentClient.createWorkoutDraft(any(), eq("req-conversation-focus")))
                .thenReturn(agentDraft("req-conversation-focus", "EX_PULLDOWN", "EX_ROW"));

        service.createForConversation(7L, today,
                "训练要求：背部训练60分钟。饮食建议：鸡胸肉糙米饭。",
                "帮我生成明天的背部训练，60分钟", "req-conversation-focus");

        ArgumentCaptor<WorkoutDraftAgentRequest> request = ArgumentCaptor.forClass(WorkoutDraftAgentRequest.class);
        verify(agentClient).createWorkoutDraft(request.capture(), eq("req-conversation-focus"));
        assertEquals(List.of("BACK"), request.getValue().trainingIntent().focusRequirements().stream()
                .map(WorkoutDraftAgentRequest.FocusRequirement::region).toList());
        assertEquals(List.of("EX_PULLDOWN", "EX_ROW"), request.getValue().candidates().stream()
                .map(WorkoutDraftAgentRequest.Candidate::exerciseCode).toList());
        assertTrue(request.getValue().userRequest().contains("鸡胸肉"));
    }

    @Test
    void removeExerciseKeepsSessionExclusionAndRecalculatesDraftWithoutAgentCall() {
        stubContext();
        WorkoutDraftResponse current = agentDraft("req-remove", "EX_ONE", "EX_TWO", "EX_DUMBBELL");

        WorkoutDraftResponse result = service.remove(7L, today, current, "EX_DUMBBELL");

        assertEquals(List.of("EX_ONE", "EX_TWO"), result.exercises().stream()
                .map(WorkoutDraftResponse.ExerciseDraft::exerciseCode).toList());
        assertEquals(List.of("EX_DUMBBELL"), result.sessionExcludedExerciseCodes());
        assertEquals(30, result.estimatedDurationMinutes());
        verify(agentClient, never()).createWorkoutDraft(any(), any());
    }

    @Test
    void removeRefusesToLeaveFewerThanTwoExercises() {
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.remove(7L, today, agentDraft("req-remove-min", "EX_ONE", "EX_TWO"), "EX_TWO"));

        assertTrue(error.getMessage().contains("至少需要保留2个动作"));
    }

    @Test
    void invalidDraftGetsExactlyOneCorrection() {
        stubContext();
        when(agentClient.createWorkoutDraft(any(), eq("req-2")))
                .thenReturn(agentDraft("req-2", "EX_UNKNOWN", "EX_TWO"))
                .thenReturn(agentDraft("req-2", "EX_ONE", "EX_TWO"));

        WorkoutDraftResponse result = service.create(7L, today, "今天练全身", null, "req-2");

        assertEquals(1, result.correctionCount());
        verify(agentClient, times(2)).createWorkoutDraft(any(), eq("req-2"));
    }

    @Test
    void replaceRejectsWhenOnlyOtherPrimaryRegionsAreAvailable() {
        stubContext();
        WorkoutDraftResponse current = agentDraft("req-replace-none", "EX_ONE", "EX_TWO");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replace(7L, today, current, "今天练胸和背", "EX_ONE", "req-replace-none"));

        assertTrue(error.getMessage().contains("没有同部位的其他可替换动作"));
        verify(agentClient, never()).createWorkoutDraft(any(), any());
    }

    @Test
    void replaceUsesDeterministicSameRegionAlternativeWithoutCallingAgent() {
        stubMultiRegionContext();
        WorkoutDraftResponse current = withUserWeight(
                longDraft("old-request", "EX_SHOULDER", "EX_CORE_ONE"), "EX_SHOULDER", "15.0");

        WorkoutDraftResponse result = service.replace(7L, today, current,
                "今天练肩和腹部", "EX_CORE_ONE", "req-replace");

        assertEquals(List.of("EX_SHOULDER", "EX_CORE_TWO"), result.exercises().stream()
                .map(WorkoutDraftResponse.ExerciseDraft::exerciseCode).toList());
        assertEquals(1, result.exercises().get(0).sets());
        assertEquals(new BigDecimal("15.0"), result.exercises().get(0).weight());
        assertEquals(4, result.exercises().get(1).sets());
        assertEquals("req-replace", result.requestId());
        assertEquals("READY", result.status());
        assertEquals("fake", result.provider());
        assertEquals(List.of("EX_CORE_ONE"), result.sessionExcludedExerciseCodes());
        assertTrue(result.assistantReply().contains("EX_CORE_ONE"));
        assertTrue(result.assistantReply().contains("仰卧举腿"));
        verify(agentClient, never()).createWorkoutDraft(any(), any());
    }

    @Test
    void repeatedReplacementDoesNotCycleBackToAnExerciseAlreadyReplacedThisSession() {
        stubMultiRegionContext();
        WorkoutDraftResponse current = longDraft("old-request", "EX_SHOULDER", "EX_CORE_ONE");
        WorkoutDraftResponse first = service.replace(7L, today, current,
                "今天练肩和腹部", "EX_CORE_ONE", "req-replace-first");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.replace(7L, today, first,
                        "今天练肩和腹部", "EX_CORE_TWO", "req-replace-second"));

        assertTrue(error.getMessage().contains("没有同部位的其他可替换动作"));
        verify(agentClient, never()).createWorkoutDraft(any(), any());
    }

    @Test
    void createUsesRecentCompletedWeightAndLeavesUnknownLoadForUser() {
        stubContext();
        var historical = storedExercise("哑铃动作");
        historical.setWeight(new BigDecimal("12.50"));
        when(exerciseMapper.findCompletedLoadsByNames(eq(7L), any())).thenReturn(List.of(historical));
        when(agentClient.createWorkoutDraft(any(), eq("req-load")))
                .thenReturn(agentDraft("req-load", "EX_ONE", "EX_DUMBBELL"));

        WorkoutDraftResponse result = service.create(7L, today, "今天练全身", null, "req-load");

        WorkoutDraftResponse.ExerciseDraft bodyweight = result.exercises().get(0);
        WorkoutDraftResponse.ExerciseDraft dumbbell = result.exercises().get(1);
        assertNull(bodyweight.weight());
        assertEquals("BODYWEIGHT", bodyweight.loadType());
        assertEquals(new BigDecimal("12.50"), dumbbell.weight());
        assertEquals("HISTORY", dumbbell.weightSource());
    }

    @Test
    void confirmRejectsLoadedExerciseWithoutWeight() {
        stubContext();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.confirm(7L, today, agentDraft("req-missing-load", "EX_ONE", "EX_DUMBBELL")));

        assertTrue(error.getMessage().contains("哑铃动作"));
        assertTrue(error.getMessage().contains("训练重量"));
        verify(workoutService, never()).saveWorkout(any());
    }

    @Test
    void confirmPersistsUserEditedWeight() {
        stubContext();
        when(workoutService.getWorkoutByDate(7L, today)).thenReturn(null);
        when(workoutService.saveWorkout(any())).thenAnswer(invocation -> invocation.getArgument(0));
        WorkoutDraftResponse draft = withUserWeight(
                agentDraft("req-user-load", "EX_ONE", "EX_DUMBBELL"), "EX_DUMBBELL", "14.0");

        Workout saved = service.confirm(7L, today, draft);

        assertEquals(new BigDecimal("14.0"), saved.getExercises().get(1).getWeight());
    }

    @Test
    void followUpCarriesPreviousDraftAndRequiresEveryPrimaryRegionWithoutHardDurationValidation() {
        stubMultiRegionContext();
        WorkoutDraftResponse previous = longDraft("old", "EX_CHEST_PRESS", "EX_CORE_ONE");
        when(agentClient.createWorkoutDraft(any(), eq("req-multi")))
                .thenReturn(longDraft("req-multi", "EX_CHEST_PRESS", "EX_CORE_ONE"))
                .thenReturn(longDraft("req-multi", "EX_SHOULDER", "EX_CORE_ONE"));

        WorkoutDraftResponse result = service.create(7L, today,
                "我今天想练肩膀，然后再补充一个练腹的动作，时间控制在1-1.5小时之间\n"
                        + "用户补充：你这个肩膀的动作归类不正确呀，再生成一次",
                previous, "req-multi");

        ArgumentCaptor<WorkoutDraftAgentRequest> requests = ArgumentCaptor.forClass(WorkoutDraftAgentRequest.class);
        verify(agentClient, times(2)).createWorkoutDraft(requests.capture(), eq("req-multi"));
        WorkoutDraftAgentRequest first = requests.getAllValues().get(0);
        WorkoutDraftAgentRequest correction = requests.getAllValues().get(1);
        assertEquals(1, first.currentExercises().size());
        assertEquals("EX_CORE_ONE", first.currentExercises().get(0).exerciseCode());
        assertEquals(60, first.trainingIntent().minimumDurationMinutes());
        assertEquals(90, first.trainingIntent().maximumDurationMinutes());
        assertTrue(first.candidates().stream().noneMatch(item -> "EX_CHEST_PRESS".equals(item.exerciseCode())));
        assertTrue(correction.validationIssues().contains("缺少肩部主训练动作"));
        assertEquals(List.of("EX_SHOULDER", "EX_CORE_ONE"),
                result.exercises().stream().map(WorkoutDraftResponse.ExerciseDraft::exerciseCode).toList());
        assertEquals(90, result.estimatedDurationMinutes());
        assertEquals(1, result.correctionCount());
    }

    @Test
    void confirmPersistsPlannedWorkoutOnlyAfterExplicitCall() {
        stubContext();
        when(workoutService.getWorkoutByDate(7L, today)).thenReturn(null);
        when(workoutService.saveWorkout(any())).thenAnswer(invocation -> invocation.getArgument(0));

        WorkoutDraftResponse edited = withSets(agentDraft("req-3", "EX_ONE", "EX_TWO"), "EX_ONE", 6);
        Workout saved = service.confirm(7L, today, edited);

        assertEquals(WorkoutStatus.PLANNED, saved.getStatus());
        assertEquals(2, saved.getExercises().size());
        assertEquals(6, saved.getExercises().get(0).getSets());
        assertNull(saved.getExercises().get(0).getWeight());
        assertNotNull(saved.getEstimatedCalories());
        verify(agentClient, never()).createWorkoutDraft(any(), any());
        verify(workoutService).saveWorkout(any());
    }

    @Test
    void confirmRefusesToOverwriteExistingWorkout() {
        Workout existing = new Workout();
        existing.setStatus(WorkoutStatus.COMPLETED);
        when(workoutService.getWorkoutByDate(7L, today)).thenReturn(existing);

        stubContext();

        assertThrows(BusinessException.class,
                () -> service.confirm(7L, today, agentDraft("req-4", "EX_ONE", "EX_TWO")));
        verify(workoutService, never()).saveWorkout(any());
    }

    @Test
    void confirmReplacesCancelledWorkoutWithNewPlannedExercises() {
        stubContext();
        Workout cancelled = new Workout();
        cancelled.setWorkoutId("cancelled-workout");
        cancelled.setStatus(WorkoutStatus.CANCELLED);
        cancelled.setExercises(List.of(storedExercise("旧动作")));
        when(workoutService.getWorkoutByDate(7L, today)).thenReturn(cancelled);
        when(workoutService.saveWorkout(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Workout saved = service.confirm(7L, today, agentDraft("req-replan", "EX_ONE", "EX_TWO"));

        assertEquals("cancelled-workout", saved.getWorkoutId());
        assertEquals(WorkoutStatus.PLANNED, saved.getStatus());
        assertEquals(List.of("动作一", "动作二"), saved.getExercises().stream().map(Exercise::getName).toList());
        verify(workoutService).saveWorkout(saved);
    }

    @Test
    void repeatedConfirmationReturnsTheSamePlannedWorkoutWithoutSavingAgain() {
        stubContext();
        Workout existing = new Workout();
        existing.setStatus(WorkoutStatus.PLANNED);
        existing.setExercises(List.of(
                storedExercise("动作一"), storedExercise("动作二")));
        when(workoutService.getWorkoutByDate(7L, today)).thenReturn(existing);

        Workout result = service.confirm(7L, today, agentDraft("req-5", "EX_ONE", "EX_TWO"));

        assertEquals(existing, result);
        verify(workoutService, never()).saveWorkout(any());
    }

    private void stubContext() {
        when(preferenceService.get(7L)).thenReturn(new TrainingPreferenceResponse(
                "BEGINNER", 3, 30, "HOME", "GENERAL_FITNESS",
                List.of("无", "哑铃"), List.of("SHOULDER"), List.of("EX_ONE"),
                List.of("EX_EXCLUDED"), 5, 7, LocalDateTime.now()));
        when(exerciseRepository.findByEnabledTrueAndFollowAlongEnabledTrueOrderBySortOrderDescNameAsc())
                .thenReturn(List.of(
                        exercise("EX_ONE", "动作一", "无", null),
                        exercise("EX_TWO", "动作二", "无", null),
                        exercise("EX_DUMBBELL", "哑铃动作", "哑铃", null),
                        exercise("EX_MACHINE", "器械动作", "史密斯机", null),
                        exercise("EX_LIMITED", "肩部限制动作", "无", "SHOULDER"),
                        exercise("EX_EXCLUDED", "排除动作", "无", null)));
        stubUserAndNutrition();
    }

    private void stubMultiRegionContext() {
        when(preferenceService.get(7L)).thenReturn(new TrainingPreferenceResponse(
                "INTERMEDIATE", 4, 45, "GYM", "MUSCLE_GAIN",
                List.of(), List.of(), List.of(), List.of(), 6, 8, null));
        ExerciseDatabase chest = exercise("EX_CHEST_PRESS", "器械推胸", "推胸机", null);
        chest.setPrimaryMuscles("胸部,肱三头肌,肩部");
        ExerciseDatabase shoulder = exercise("EX_SHOULDER", "器械肩推", "肩推机", null);
        shoulder.setPrimaryMuscles("肩部,肱三头肌");
        ExerciseDatabase coreOne = exercise("EX_CORE_ONE", "卷腹", "无", null);
        coreOne.setPrimaryMuscles("腹部");
        ExerciseDatabase coreTwo = exercise("EX_CORE_TWO", "仰卧举腿", "无", null);
        coreTwo.setPrimaryMuscles("腹部,髋屈肌");
        when(exerciseRepository.findByEnabledTrueAndFollowAlongEnabledTrueOrderBySortOrderDescNameAsc())
                .thenReturn(List.of(chest, shoulder, coreOne, coreTwo));
        User user = new User();
        user.setWeight(new BigDecimal("70"));
        lenient().when(userService.findById(7L)).thenReturn(user);
        when(userService.calculateDailyCalories(user)).thenReturn(2500.0);
        when(compareService.calculateDailyIntake(7L, today)).thenReturn(0.0);
        when(recipeTemplateMapper.searchEnabled(any(), any(), any(), eq(0), eq(100)))
                .thenReturn(List.of());
    }

    private ExerciseDatabase exercise(String code, String name, String equipment, String limitations) {
        ExerciseDatabase item = new ExerciseDatabase();
        item.setExerciseCode(code);
        item.setName(name);
        item.setCategory("力量");
        item.setPrimaryMuscles("EX_ONE".equals(code) ? "胸部" : "背部");
        item.setEquipment(equipment);
        item.setIntensity("中");
        item.setMet(new BigDecimal("5.0"));
        item.setLimitationTags(limitations);
        item.setDefaultSets(3);
        item.setDefaultReps(10);
        item.setDefaultDurationSeconds(40);
        item.setDefaultRestSeconds(60);
        item.setDefaultRpe(6);
        return item;
    }

    private void stubUserAndNutrition() {
        User user = new User();
        user.setWeight(new BigDecimal("70"));
        lenient().when(userService.findById(7L)).thenReturn(user);
        lenient().when(userService.calculateDailyCalories(user)).thenReturn(2000.0);
        lenient().when(compareService.calculateDailyIntake(7L, today)).thenReturn(500.0);
        lenient().when(recipeTemplateMapper.searchEnabled(any(), any(), any(), eq(0), eq(100)))
                .thenReturn(List.of());
    }

    private WorkoutDraftResponse agentDraft(String requestId, String... codes) {
        List<WorkoutDraftResponse.ExerciseDraft> exercises = java.util.Arrays.stream(codes)
                .map(code -> new WorkoutDraftResponse.ExerciseDraft(
                        code, null, null, null, 3, 10, 40, 60, 6,
                        null, null, null, null, null, "测试理由"))
                .toList();
        return new WorkoutDraftResponse(requestId, "READY", "fake", "今日训练", "已生成", "今天练全身",
                null, 20, 6, exercises, List.of(), "仅供健身参考", null, null, List.of());
    }

    private WorkoutDraftResponse longDraft(String requestId, String... codes) {
        List<WorkoutDraftResponse.ExerciseDraft> exercises = java.util.Arrays.stream(codes)
                .map(code -> new WorkoutDraftResponse.ExerciseDraft(
                        code, null, null, null, 1, 10, 1800, 0, 7,
                        null, null, null, null, null, "测试理由"))
                .toList();
        return new WorkoutDraftResponse(requestId, "READY", "fake", "肩腹训练", "已生成", null,
                null, 60, 7, exercises, List.of(), "仅供健身参考", null, null, List.of());
    }

    private WorkoutDraftResponse withUserWeight(WorkoutDraftResponse draft, String code, String weight) {
        List<WorkoutDraftResponse.ExerciseDraft> exercises = draft.exercises().stream().map(item ->
                code.equals(item.exerciseCode())
                        ? new WorkoutDraftResponse.ExerciseDraft(item.exerciseCode(), item.name(), item.equipment(),
                        item.primaryMuscles(), item.sets(), item.reps(), item.durationSeconds(), item.restSeconds(),
                        item.rpe(), new BigDecimal(weight), "EXTERNAL_LOAD", "USER", "用户填写", item.calories(),
                        item.rationale())
                        : item).toList();
        return new WorkoutDraftResponse(draft.requestId(), draft.status(), draft.provider(), draft.title(),
                draft.assistantReply(), draft.userRequest(), draft.date(), draft.estimatedDurationMinutes(),
                draft.targetRpe(), exercises, draft.warnings(), draft.disclaimer(), draft.correctionCount(),
                draft.nutritionPlan(), draft.sessionExcludedExerciseCodes());
    }

    private WorkoutDraftResponse withSets(WorkoutDraftResponse draft, String code, int sets) {
        List<WorkoutDraftResponse.ExerciseDraft> exercises = draft.exercises().stream().map(item ->
                code.equals(item.exerciseCode())
                        ? new WorkoutDraftResponse.ExerciseDraft(item.exerciseCode(), item.name(), item.equipment(),
                        item.primaryMuscles(), sets, item.reps(), item.durationSeconds(), item.restSeconds(), item.rpe(),
                        item.weight(), item.loadType(), item.weightSource(), item.weightNote(), item.calories(),
                        item.rationale())
                        : item).toList();
        return new WorkoutDraftResponse(draft.requestId(), draft.status(), draft.provider(), draft.title(),
                draft.assistantReply(), draft.userRequest(), draft.date(), draft.estimatedDurationMinutes(),
                draft.targetRpe(), exercises, draft.warnings(), draft.disclaimer(), draft.correctionCount(),
                draft.nutritionPlan(), draft.sessionExcludedExerciseCodes());
    }

    private com.fitness.entity.Exercise storedExercise(String name) {
        var exercise = new com.fitness.entity.Exercise();
        exercise.setName(name);
        exercise.setSets(3);
        exercise.setReps(10);
        return exercise;
    }
}
