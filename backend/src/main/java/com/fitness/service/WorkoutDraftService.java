package com.fitness.service;

import com.fitness.ai.AgentClient;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.TrainingPreferenceResponse;
import com.fitness.dto.WorkoutDraftAgentRequest;
import com.fitness.dto.WorkoutDraftResponse;
import com.fitness.entity.Exercise;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.SystemRecipeTemplate;
import com.fitness.entity.User;
import com.fitness.entity.Workout;
import com.fitness.entity.WorkoutStatus;
import com.fitness.mapper.ExerciseMapper;
import com.fitness.repository.ExerciseDatabaseRepository;
import com.fitness.mapper.SystemRecipeTemplateMapper;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 编排 A5 草稿生成、Java 确定性校验以及用户确认后的计划保存。 */
@Service
@Transactional(readOnly = true)
public class WorkoutDraftService {
    private static final Logger log = LoggerFactory.getLogger(WorkoutDraftService.class);
    private static final int MIN_DRAFT_EXERCISES = 2;
    private static final int DEFAULT_DRAFT_SETS = 4;
    private static final int MAX_AGENT_CANDIDATES = 36;
    private final AgentClient agentClient;
    private final TrainingPreferenceService preferenceService;
    private final ExerciseDatabaseRepository exerciseRepository;
    private final ExerciseMapper exerciseMapper;
    private final WorkoutService workoutService;
    private final UserService userService;
    private final CompareService compareService;
    private final SystemRecipeTemplateMapper recipeTemplateMapper;
    private final String businessZone;
    private static final Pattern MINUTE_RANGE_PATTERN = Pattern.compile(
            "(\\d{1,3})\\s*(?:分钟)?\\s*(?:-|~|～|到|至)\\s*(\\d{1,3})\\s*(?:分钟|min(?:ute)?s?)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern HOUR_RANGE_PATTERN = Pattern.compile(
            "(\\d+(?:\\.\\d+)?)\\s*(?:小时)?\\s*(?:-|~|～|到|至)\\s*(\\d+(?:\\.\\d+)?)\\s*小时");
    private static final Pattern DURATION_PATTERN = Pattern.compile(
            "(\\d{1,3})\\s*(?:分钟|min(?:ute)?s?)", Pattern.CASE_INSENSITIVE);
    private static final Pattern HOUR_PATTERN = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*小时");
    private static final Pattern NEGATED_REGION_PREFIX_PATTERN = Pattern.compile(
            ".*(?:不要|不想|不做|不练|不进行|不安排|不包含|不涉及|别做|别练|别安排|避免|排除|禁止)"
                    + "(?:任何|所有|做|练|进行|安排|包含|涉及|针对|加入|选择|有关|相关)*$");

    public WorkoutDraftService(AgentClient agentClient,
                               TrainingPreferenceService preferenceService,
                               ExerciseDatabaseRepository exerciseRepository,
                               ExerciseMapper exerciseMapper,
                               WorkoutService workoutService,
                               UserService userService,
                               CompareService compareService,
                               SystemRecipeTemplateMapper recipeTemplateMapper,
                               @Value("${fitness.business-zone:Asia/Shanghai}") String businessZone) {
        this.agentClient = agentClient;
        this.preferenceService = preferenceService;
        this.exerciseRepository = exerciseRepository;
        this.exerciseMapper = exerciseMapper;
        this.workoutService = workoutService;
        this.userService = userService;
        this.compareService = compareService;
        this.recipeTemplateMapper = recipeTemplateMapper;
        this.businessZone = businessZone;
    }

    public WorkoutDraftResponse create(Long userId, LocalDate date, String userRequest,
                                       WorkoutDraftResponse currentDraft, String requestId) {
        validateDate(date);
        ensureDraftSlotAvailable(userId, date);
        String normalized = normalizeUserRequest(userRequest);
        return generate(userId, date, normalized, normalized, currentDraft, null, requestId);
    }

    /**
     * A7 可让模型补充动作说明，但训练部位、时长与排除项只能来自用户本轮原句。
     * 这样饮食名称或历史摘要不会扩大本轮训练目标。
     */
    public WorkoutDraftResponse createForConversation(Long userId, LocalDate date, String modelRequest,
                                                      String latestUserRequest, String requestId) {
        validateDate(date);
        ensureDraftSlotAvailable(userId, date);
        return generate(userId, date, normalizeUserRequest(modelRequest),
                normalizeUserRequest(latestUserRequest), null, null, requestId);
    }

    private void ensureDraftSlotAvailable(Long userId, LocalDate date) {
        Workout existing = workoutService.getWorkoutByDate(userId, date);
        if (existing == null || existing.getStatus() == WorkoutStatus.CANCELLED) return;
        if (existing.getStatus() == WorkoutStatus.COMPLETED) {
            throw new IllegalArgumentException("该日期训练已完成，不能再生成待确认计划；请明确选择明天或其他未来日期");
        }
        throw new IllegalArgumentException("该日期已有训练计划，请先在训练页查看或处理现有计划");
    }

    public WorkoutDraftResponse replace(Long userId, LocalDate date, WorkoutDraftResponse current,
                                        String userRequest, String exerciseCode, String requestId) {
        long started = System.nanoTime();
        validateDate(date);
        if (current == null || current.exercises() == null
                || current.exercises().stream().noneMatch(item -> exerciseCode.equals(item.exerciseCode()))) {
            throw new IllegalArgumentException("待替换动作不在当前草稿中");
        }
        String normalized = normalizeUserRequest(userRequest);
        GenerationContext context = context(userId, normalized, normalized, sessionExclusions(current));
        Map<String, ExerciseDatabase> candidates = candidateMap(context.candidates());
        ExerciseDatabase replaced = candidates.get(exerciseCode);
        if (replaced == null) {
            throw new IllegalArgumentException("原动作已不符合当前训练偏好，请重新生成整份草稿");
        }
        Set<String> currentCodes = current.exercises().stream()
                .map(WorkoutDraftResponse.ExerciseDraft::exerciseCode)
                .collect(java.util.stream.Collectors.toSet());
        String replacedRegion = primaryRegion(replaced);
        ExerciseDatabase alternative = context.candidates().stream()
                .filter(item -> !currentCodes.contains(item.getExerciseCode()))
                .filter(item -> replacedRegion.equals(primaryRegion(item)))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("当前偏好下没有同部位的其他可替换动作"));

        WorkoutDraftResponse.ExerciseDraft replacement = replacementExercise(alternative, context.preferences());
        List<WorkoutDraftResponse.ExerciseDraft> exercises = current.exercises().stream()
                .map(item -> exerciseCode.equals(item.exerciseCode()) ? replacement : item)
                .toList();
        List<String> sessionExcluded = new ArrayList<>(sessionExclusions(current));
        if (!sessionExcluded.contains(exerciseCode)) sessionExcluded.add(exerciseCode);
        String replacedName = current.exercises().stream()
                .filter(item -> exerciseCode.equals(item.exerciseCode()))
                .map(WorkoutDraftResponse.ExerciseDraft::name)
                .filter(name -> name != null && !name.isBlank())
                .findFirst().orElse(exerciseCode);
        WorkoutDraftResponse deterministic = new WorkoutDraftResponse(
                requestId, current.status(), current.provider(), current.title(),
                "已将“" + replacedName + "”替换为“" + alternative.getName() + "”，其他动作保持不变。",
                normalized, date, current.estimatedDurationMinutes(), current.targetRpe(), exercises,
                current.warnings() == null ? List.of() : current.warnings(), current.disclaimer(),
                current.correctionCount() == null ? 0 : current.correctionCount(), null,
                List.copyOf(sessionExcluded));
        List<String> issues = validateDraft(deterministic, context.preferences(), context.candidates(),
                context.intent(), current, exerciseCode);
        if (!issues.isEmpty()) {
            log.warn("AI_WORKOUT_DETERMINISTIC_REPLACEMENT_FAILED requestId={} issues={}", requestId, issues);
            throw new IllegalArgumentException("替换后训练计划不再满足本次要求：" + issues.get(0));
        }
        WorkoutDraftResponse normalizedDraft = normalize(deterministic, date, context, userWeight(userId),
                deterministic.correctionCount(), normalized, sessionExcluded, current);
        log.info("AI_WORKOUT_DETERMINISTIC_REPLACEMENT_COMPLETED requestId={} from={} to={} candidateCount={} durationMs={}",
                requestId, exerciseCode, alternative.getExerciseCode(), context.candidates().size(),
                Math.round((System.nanoTime() - started) / 1_000_000.0));
        return withNutritionPlan(normalizedDraft, userId, date, context.preferences());
    }

    /** 换动作属于确定性目录操作；无需让模型重新输出整份草稿。 */
    private WorkoutDraftResponse.ExerciseDraft replacementExercise(
            ExerciseDatabase candidate, TrainingPreferenceResponse preferences) {
        int rpe = candidate.getDefaultRpe() == null ? preferences.targetRpeMin() : candidate.getDefaultRpe();
        rpe = Math.min(preferences.targetRpeMax(), Math.max(preferences.targetRpeMin(), rpe));
        return new WorkoutDraftResponse.ExerciseDraft(
                candidate.getExerciseCode(), candidate.getName(), candidate.getEquipment(),
                candidate.getPrimaryMuscles(), DEFAULT_DRAFT_SETS,
                candidate.getDefaultReps() == null ? 10 : candidate.getDefaultReps(),
                candidate.getDefaultDurationSeconds() == null ? 45 : candidate.getDefaultDurationSeconds(),
                candidate.getDefaultRestSeconds() == null ? 60 : candidate.getDefaultRestSeconds(),
                rpe, null, null, null, null, null,
                "符合当前器械、限制和训练目标的同部位替代动作");
    }

    public WorkoutDraftResponse remove(Long userId, LocalDate date, WorkoutDraftResponse current,
                                       String exerciseCode) {
        validateDate(date);
        if (current == null || current.exercises() == null
                || current.exercises().stream().noneMatch(item -> exerciseCode.equals(item.exerciseCode()))) {
            throw new IllegalArgumentException("待删除动作不在当前草稿中");
        }
        if (current.exercises().size() <= MIN_DRAFT_EXERCISES) {
            throw new IllegalArgumentException("训练草稿至少需要保留2个动作，可以改用换一个");
        }
        List<String> sessionExcluded = new ArrayList<>(sessionExclusions(current));
        if (!sessionExcluded.contains(exerciseCode)) sessionExcluded.add(exerciseCode);
        GenerationContext context = context(userId, current.userRequest(), current.userRequest(), sessionExcluded);
        List<WorkoutDraftResponse.ExerciseDraft> remaining = current.exercises().stream()
                .filter(item -> !exerciseCode.equals(item.exerciseCode()))
                .toList();
        WorkoutDraftResponse removed = new WorkoutDraftResponse(
                current.requestId(), current.status(), current.provider(), current.title(),
                current.assistantReply(), current.userRequest(), date, current.estimatedDurationMinutes(),
                current.targetRpe(), remaining, current.warnings(), current.disclaimer(),
                current.correctionCount(), null, List.copyOf(sessionExcluded));
        List<String> issues = validateDraft(removed, context.preferences(), context.candidates(), context.intent(),
                null, null);
        if (!issues.isEmpty()) {
            throw new IllegalArgumentException("删除后训练计划不再满足本次要求：" + issues.get(0) + "，可以改用换一个");
        }
        WorkoutDraftResponse normalized = normalize(removed, date, context, userWeight(userId),
                current.correctionCount() == null ? 0 : current.correctionCount(), current.userRequest(),
                sessionExcluded, current);
        return withNutritionPlan(normalized, userId, date, context.preferences());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.SERIALIZABLE)
    public Workout confirm(Long userId, LocalDate date, WorkoutDraftResponse draft) {
        validateDate(date);
        GenerationContext context = context(userId, draft == null ? null : draft.userRequest(),
                draft == null ? null : draft.userRequest(), sessionExclusions(draft));
        List<String> issues = validateDraft(draft, context.preferences(), context.candidates(), context.intent(),
                null, null);
        if (!issues.isEmpty()) throw new IllegalArgumentException("训练草稿已失效：" + issues.get(0));
        validateConfirmedLoads(draft, context.candidates());
        WorkoutDraftResponse normalized = normalize(draft, date, context, userWeight(userId), 0,
                draft == null ? null : draft.userRequest(), sessionExclusions(draft), draft);

        Workout existing = workoutService.getWorkoutByDate(userId, date);
        if (existing != null) {
            if (samePlan(existing, normalized)) return existing;
            if (existing.getStatus() != WorkoutStatus.CANCELLED) {
                throw new BusinessException(ApiErrorCode.CONFLICT, "该日期已有训练，请先返回训练页处理");
            }
        }

        Workout workout = new Workout();
        workout.setUserId(userId);
        workout.setDate(date);
        // 取消记录仍占用同一用户同一天的唯一位置；确认新草稿时复用其业务编号并整体替换动作。
        if (existing != null) workout.setWorkoutId(existing.getWorkoutId());
        workout.setStatus(WorkoutStatus.PLANNED);
        BigDecimal total = normalized.exercises().stream().map(WorkoutDraftResponse.ExerciseDraft::calories)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
        workout.setTotalCalories(total);
        workout.setEstimatedCalories(total);
        workout.setEstimatedDurationMinutes(normalized.estimatedDurationMinutes());
        workout.setCalorieReferenceWeight(userWeight(userId));
        List<Integer> calorieDurations = WorkoutCalorieFormula.allocateDurationSeconds(
                normalized.estimatedDurationMinutes(),
                normalized.exercises().stream().map(WorkoutDraftResponse.ExerciseDraft::sets).toList());
        List<Exercise> persistedExercises = new ArrayList<>();
        for (int index = 0; index < normalized.exercises().size(); index++) {
            WorkoutDraftResponse.ExerciseDraft item = normalized.exercises().get(index);
            Exercise exercise = new Exercise();
            exercise.setName(item.name());
            // null 表示自重或无需公斤数；不得转成 0，否则训练记录会错误展示为 0 kg。
            exercise.setWeight(item.weight());
            exercise.setSets(item.sets());
            exercise.setReps(item.reps());
            exercise.setCalories(item.calories());
            exercise.setCalorieDurationSeconds(calorieDurations.get(index));
            persistedExercises.add(exercise);
        }
        workout.setExercises(List.copyOf(persistedExercises));
        return workoutService.saveWorkout(workout);
    }

    /** 网络结果未知时，同一份已保存计划可安全重试；不同计划绝不覆盖同日记录。 */
    private boolean samePlan(Workout existing, WorkoutDraftResponse draft) {
        if (existing.getStatus() != WorkoutStatus.PLANNED || existing.getExercises() == null
                || existing.getExercises().size() != draft.exercises().size()) return false;
        for (int index = 0; index < existing.getExercises().size(); index++) {
            Exercise stored = existing.getExercises().get(index);
            WorkoutDraftResponse.ExerciseDraft proposed = draft.exercises().get(index);
            if (!stored.getName().equals(proposed.name())
                    || !stored.getSets().equals(proposed.sets())
                    || !stored.getReps().equals(proposed.reps())
                    || !sameDecimal(stored.getWeight(), proposed.weight())) return false;
        }
        return true;
    }

    private boolean sameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) return left == null && right == null;
        return left.compareTo(right) == 0;
    }

    private WorkoutDraftResponse generate(Long userId, LocalDate date, String userRequest, String intentRequest,
                                          WorkoutDraftResponse current, String replaceCode, String requestId) {
        GenerationContext context = context(userId, userRequest, intentRequest, sessionExclusions(current));
        if (context.candidates().size() < 2) {
            throw new IllegalArgumentException("符合当前器械和限制的动作不足，请调整训练偏好");
        }
        if (replaceCode != null && current != null) {
            Set<String> currentCodes = current.exercises().stream()
                    .map(WorkoutDraftResponse.ExerciseDraft::exerciseCode).collect(java.util.stream.Collectors.toSet());
            String replacedRegion = context.candidates().stream()
                    .filter(item -> replaceCode.equals(item.getExerciseCode()))
                    .map(this::primaryRegion).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("原动作已不符合当前训练偏好，请重新生成整份草稿"));
            boolean hasAlternative = context.candidates().stream()
                    .anyMatch(item -> !currentCodes.contains(item.getExerciseCode())
                            && replacedRegion.equals(primaryRegion(item)));
            if (!hasAlternative) throw new IllegalArgumentException("当前偏好下没有同部位的其他可替换动作");
        }
        WorkoutDraftAgentRequest firstRequest = agentRequest(
                requestId, date, userRequest, context, current, replaceCode, List.of());
        WorkoutDraftResponse first = agentClient.createWorkoutDraft(firstRequest, requestId);
        List<String> issues = validateDraft(first, context.preferences(), context.candidates(), context.intent(),
                current, replaceCode);
        int correctionCount = 0;
        WorkoutDraftResponse accepted = first;
        if (!issues.isEmpty()) {
            log.warn("AI_WORKOUT_DRAFT_VALIDATION_FAILED requestId={} attempt=1 issues={}", requestId, issues);
            WorkoutDraftAgentRequest correctionRequest = agentRequest(
                    requestId, date, userRequest, context, current, replaceCode, issues);
            accepted = agentClient.createWorkoutDraft(correctionRequest, requestId);
            correctionCount = 1;
            issues = validateDraft(accepted, context.preferences(), context.candidates(), context.intent(),
                    current, replaceCode);
        }
        if (!issues.isEmpty()) {
            log.warn("AI_WORKOUT_DRAFT_VALIDATION_FAILED requestId={} attempt=2 issues={}", requestId, issues);
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "AI训练草稿未通过规则校验，请重新生成");
        }
        WorkoutDraftResponse normalized = normalize(accepted, date, context, userWeight(userId), correctionCount,
                userRequest, sessionExclusions(current), current);
        return withNutritionPlan(normalized, userId, date, context.preferences());
    }

    private GenerationContext context(Long userId, String userRequest, String intentRequest,
                                      List<String> sessionExcludedCodes) {
        TrainingPreferenceResponse stored = preferenceService.get(userId);
        WorkoutDraftAgentRequest.TrainingIntent intent = parseIntent(intentRequest, stored.sessionMinutes());
        if (stored.updatedAt() == null && intent.focusRequirements().isEmpty()) {
            throw new IllegalArgumentException("请先告诉助手今天想练哪个部位，例如胸部、背部、肩部或腿部");
        }
        TrainingPreferenceResponse preferences = effectivePreferences(stored, intent);
        Set<String> equipment = new HashSet<>(preferences.equipmentTags());
        Set<String> limitations = new HashSet<>(preferences.limitationTags());
        Set<String> excluded = new HashSet<>(preferences.excludedExerciseCodes());
        excluded.addAll(sessionExcludedCodes == null ? List.of() : sessionExcludedCodes);
        Set<String> preferred = new HashSet<>(preferences.preferredExerciseCodes());
        boolean starterRecommendation = stored.updatedAt() == null;
        List<ExerciseDatabase> eligible = exerciseRepository
                .findByEnabledTrueAndFollowAlongEnabledTrueOrderBySortOrderDescNameAsc().stream()
                .filter(item -> equipmentAllowed(item, equipment))
                .filter(item -> !starterRecommendation || simplicityScore(item) >= 3)
                .filter(item -> !hasAnyCode(item.getLimitationTags(), limitations))
                .filter(item -> !excluded.contains(item.getExerciseCode()))
                .filter(item -> !regionExcluded(primaryRegion(item), intent.excludedRegions()))
                .filter(item -> !temporarilyExcluded(item, userRequest))
                // 有明确目标时不把其他部位送给模型，既缩小 Prompt，也阻止模型扩大训练范围。
                .filter(item -> intent.focusRequirements().isEmpty()
                        || intent.focusRequirements().stream()
                        .anyMatch(requirement -> regionMatches(requirement.region(), primaryRegion(item))))
                .sorted((left, right) -> {
                    int focus = Integer.compare(focusScore(right, intent), focusScore(left, intent));
                    if (focus != 0) return focus;
                    int preference = Boolean.compare(preferred.contains(right.getExerciseCode()),
                            preferred.contains(left.getExerciseCode()));
                    if (preference != 0) return preference;
                    return Integer.compare(simplicityScore(right), simplicityScore(left));
                })
                .limit(MAX_AGENT_CANDIDATES)
                .toList();
        for (WorkoutDraftAgentRequest.FocusRequirement requirement : intent.focusRequirements()) {
            boolean available = eligible.stream().map(this::primaryRegion)
                    .anyMatch(region -> regionMatches(requirement.region(), region));
            if (!available) {
                throw new IllegalArgumentException("当前器械和限制下没有可用的" + requirement.label() + "动作");
            }
        }
        if (!intent.focusRequirements().isEmpty()) {
            long focusedCandidateCount = eligible.stream().map(this::primaryRegion)
                    .filter(region -> intent.focusRequirements().stream()
                            .anyMatch(requirement -> regionMatches(requirement.region(), region)))
                    .count();
            if (focusedCandidateCount < MIN_DRAFT_EXERCISES) {
                if (starterRecommendation) {
                    throw new IllegalArgumentException(
                            "当前部位的入门动作不足，请告诉助手你是在家还是健身房，以及可用器械");
                }
                throw new IllegalArgumentException("当前器械和限制下符合目标部位的动作不足2个，请调整训练偏好");
            }
        }
        Map<String, BigDecimal> recentWeights = recentWeights(userId, eligible);
        return new GenerationContext(preferences, eligible, intent, recentWeights);
    }

    private boolean equipmentAllowed(ExerciseDatabase exercise, Set<String> equipment) {
        if (equipment.isEmpty()) return true;
        if ("无".equals(exercise.getEquipment())) return true;
        // “无”表示用户也接受徒手动作，不应覆盖同时勾选的哑铃、杠铃或固定器械。
        return equipment.contains(exercise.getEquipment());
    }

    /** 未选偏好动作时让排序稳定倾向徒手和易上手机械，仍以主目标肌群为第一优先级。 */
    private int simplicityScore(ExerciseDatabase exercise) {
        int score = switch (exercise.getEquipment() == null ? "" : exercise.getEquipment()) {
            case "无" -> 5;
            case "推胸机", "肩推机", "高位下拉机", "坐姿划船机", "蝴蝶机", "腿举机",
                    "腿屈伸机", "腿弯举机", "提踵机", "辅助引体双杠机" -> 4;
            case "哑铃", "瑜伽垫", "绳索器械" -> 3;
            case "杠铃", "史密斯机" -> 1;
            default -> 2;
        };
        String intensity = exercise.getIntensity() == null ? "" : exercise.getIntensity();
        if (intensity.contains("低") || intensity.equalsIgnoreCase("LOW")) score += 2;
        if (intensity.contains("高") || intensity.equalsIgnoreCase("HIGH")) score -= 1;
        return score;
    }

    private boolean hasAnyCode(String csv, Set<String> codes) {
        if (csv == null || csv.isBlank() || codes.isEmpty()) return false;
        for (String value : csv.split(",")) if (codes.contains(value.trim().toUpperCase())) return true;
        return false;
    }

    /** 重量建议只采信用户显式编辑或最近一次已完成记录，避免模型凭空猜测公斤数。 */
    private WeightRecommendation weightRecommendation(WorkoutDraftResponse.ExerciseDraft generated,
                                                      WorkoutDraftResponse.ExerciseDraft previous,
                                                      ExerciseDatabase exercise,
                                                      BigDecimal recentWeight) {
        String type = loadType(exercise);
        if ("BODYWEIGHT".equals(type)) {
            return new WeightRecommendation(null, type, "BODYWEIGHT", "使用自身体重，无需填写公斤数");
        }
        if ("NO_LOAD".equals(type)) {
            return new WeightRecommendation(null, type, "NOT_APPLICABLE", "该动作无需填写外部负重");
        }
        WorkoutDraftResponse.ExerciseDraft edited = hasAcceptedWeight(previous) ? previous : generated;
        if (hasAcceptedWeight(edited)) {
            String source = "HISTORY".equals(edited.weightSource()) ? "HISTORY" : "USER";
            String note = "HISTORY".equals(source)
                    ? ("ASSISTANCE".equals(type) ? "沿用最近一次已完成记录的辅助重量" : "沿用最近一次已完成记录的重量")
                    : ("ASSISTANCE".equals(type) ? "用户填写的辅助重量" : "用户填写的训练重量");
            if (exercise.getEquipment() != null && exercise.getEquipment().contains("哑铃")) note += "（单只哑铃）";
            return new WeightRecommendation(edited.weight(), type, source, note);
        }
        if (validWeight(recentWeight)) {
            return new WeightRecommendation(recentWeight, type, "HISTORY",
                    "ASSISTANCE".equals(type) ? "沿用最近一次已完成记录的辅助重量" : "沿用最近一次已完成记录的重量");
        }
        if ("OPTIONAL_LOAD".equals(type)) {
            return new WeightRecommendation(null, type, "OPTIONAL",
                    "可使用自重，也可以填写本次计划采用的外部负重");
        }
        String note = "ASSISTANCE".equals(type)
                ? "请填写器械辅助重量；数值越大，动作通常越轻"
                : "暂无历史重量，请选择能完成目标次数并保留2～3次余力的重量";
        if (exercise.getEquipment() != null && exercise.getEquipment().contains("哑铃")) note = "按单只哑铃填写；" + note;
        return new WeightRecommendation(null, type, "UNSET", note);
    }

    private boolean hasAcceptedWeight(WorkoutDraftResponse.ExerciseDraft item) {
        return item != null && ("USER".equals(item.weightSource()) || "HISTORY".equals(item.weightSource()))
                && validWeight(item.weight());
    }

    private boolean validWeight(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0
                && value.compareTo(BigDecimal.valueOf(1000)) <= 0;
    }

    /** 根据现有动作库字段区分自重、无需公斤数、外部负重和辅助配重，无需修改数据库结构。 */
    private String loadType(ExerciseDatabase exercise) {
        String equipment = exercise.getEquipment() == null ? "" : exercise.getEquipment();
        String name = exercise.getName() == null ? "" : exercise.getName();
        String category = exercise.getCategory() == null ? "" : exercise.getCategory();
        if (equipment.contains("辅助") || name.contains("辅助")) return "ASSISTANCE";
        if (category.contains("有氧") || category.contains("灵活") || category.contains("拉伸")) return "NO_LOAD";
        if (equipment.startsWith("无或")) return "OPTIONAL_LOAD";
        if (equipment.isBlank() || "无".equals(equipment) || equipment.contains("单杠")
                || equipment.contains("双杠") || equipment.contains("垫")
                || equipment.contains("弹力带")) return "BODYWEIGHT";
        return "EXTERNAL_LOAD";
    }

    private Map<String, BigDecimal> recentWeights(Long userId, List<ExerciseDatabase> candidates) {
        List<String> names = candidates.stream().map(ExerciseDatabase::getName).distinct().toList();
        if (names.isEmpty()) return Map.of();
        Map<String, BigDecimal> result = new LinkedHashMap<>();
        exerciseMapper.findCompletedLoadsByNames(userId, names).forEach(item -> {
            if (item.getName() != null && validWeight(item.getWeight())) {
                result.putIfAbsent(item.getName(), item.getWeight());
            }
        });
        return result;
    }

    private void validateConfirmedLoads(WorkoutDraftResponse draft, List<ExerciseDatabase> candidates) {
        Map<String, ExerciseDatabase> allowed = candidateMap(candidates);
        for (WorkoutDraftResponse.ExerciseDraft item : draft.exercises()) {
            ExerciseDatabase exercise = allowed.get(item.exerciseCode());
            String type = loadType(exercise);
            if (("EXTERNAL_LOAD".equals(type) || "ASSISTANCE".equals(type))
                    && !validWeight(item.weight())) {
                throw new IllegalArgumentException("请先填写“" + exercise.getName() + "”的"
                        + ("ASSISTANCE".equals(type) ? "辅助重量" : "训练重量"));
            }
        }
    }

    private WorkoutDraftAgentRequest agentRequest(String requestId, LocalDate date, String userRequest,
                                                   GenerationContext context,
                                                   WorkoutDraftResponse current,
                                                   String replaceCode,
                                                   List<String> issues) {
        TrainingPreferenceResponse value = context.preferences();
        var preferences = new WorkoutDraftAgentRequest.Preferences(
                value.experienceLevel(), value.sessionMinutes(), value.trainingLocation(),
                value.trainingGoal(), value.equipmentTags(), value.limitationTags(),
                value.preferredExerciseCodes(), value.excludedExerciseCodes(),
                value.targetRpeMin(), value.targetRpeMax());
        List<WorkoutDraftAgentRequest.Candidate> candidates = context.candidates().stream()
                .map(item -> new WorkoutDraftAgentRequest.Candidate(
                        item.getExerciseCode(), item.getName(), item.getCategory(),
                        item.getPrimaryMuscles(), primaryRegion(item), item.getEquipment(), item.getIntensity(),
                        item.getDefaultSets(), item.getDefaultReps(), item.getDefaultDurationSeconds(),
                        item.getDefaultRestSeconds(), item.getDefaultRpe(), loadType(item),
                        context.recentWeights().get(item.getName())))
                .toList();
        Set<String> candidateCodes = context.candidates().stream()
                .map(ExerciseDatabase::getExerciseCode)
                .collect(java.util.stream.Collectors.toSet());
        List<WorkoutDraftAgentRequest.DraftExercise> currentExercises = current == null
                || current.exercises() == null ? List.of() : current.exercises().stream()
                .filter(item -> item != null && candidateCodes.contains(item.exerciseCode()))
                .limit(12)
                .map(item -> new WorkoutDraftAgentRequest.DraftExercise(
                        item.exerciseCode(), item.sets(), item.reps(), item.durationSeconds(),
                        item.restSeconds(), item.rpe(), item.weight(), item.rationale()))
                .toList();
        return new WorkoutDraftAgentRequest(requestId, date, userRequest, context.intent(), preferences, candidates,
                currentExercises, replaceCode, issues, "DRAFT_ONLY");
    }

    private List<String> validateDraft(WorkoutDraftResponse draft,
                                       TrainingPreferenceResponse preferences,
                                       List<ExerciseDatabase> eligible,
                                       WorkoutDraftAgentRequest.TrainingIntent intent,
                                       WorkoutDraftResponse current,
                                       String replaceCode) {
        List<String> issues = new ArrayList<>();
        if (draft == null || draft.exercises() == null || draft.exercises().size() < MIN_DRAFT_EXERCISES
                || draft.exercises().size() > 12) {
            return List.of("动作数量必须在2到12个之间");
        }
        Map<String, ExerciseDatabase> allowed = candidateMap(eligible);
        Set<String> seen = new HashSet<>();
        for (WorkoutDraftResponse.ExerciseDraft item : draft.exercises()) {
            if (item == null || item.exerciseCode() == null || !allowed.containsKey(item.exerciseCode())) {
                issues.add("包含动作库以外或不符合偏好的动作");
                continue;
            }
            if (!seen.add(item.exerciseCode())) issues.add("动作不能重复");
            if (item.sets() == null || item.sets() < 1 || item.sets() > 10
                    || item.reps() == null || item.reps() < 1 || item.reps() > 100) {
                issues.add("组数或次数超出允许范围");
            }
            if (item.durationSeconds() == null || item.durationSeconds() < 10
                    || item.restSeconds() == null || item.restSeconds() < 0 || item.restSeconds() > 600) {
                issues.add("动作或休息时间超出允许范围");
            }
            if (item.rpe() == null || item.rpe() < preferences.targetRpeMin()
                    || item.rpe() > preferences.targetRpeMax()) {
                issues.add("动作RPE不符合用户设置");
            }
        }
        // 模型给出的动作秒数和休息只作编排参考，不作为接受或拒绝草稿的硬条件。
        validateFocusRequirements(draft, allowed, intent, issues);
        if (replaceCode != null && current != null) validateReplacement(current, draft, replaceCode, issues);
        return issues.stream().distinct().limit(20).toList();
    }

    private void validateReplacement(WorkoutDraftResponse current, WorkoutDraftResponse draft,
                                     String replaceCode, List<String> issues) {
        if (draft.exercises().size() != current.exercises().size()) {
            issues.add("替换动作时不能改变动作数量");
            return;
        }
        for (int index = 0; index < current.exercises().size(); index++) {
            String before = current.exercises().get(index).exerciseCode();
            String after = draft.exercises().get(index).exerciseCode();
            if (replaceCode.equals(before)) {
                if (replaceCode.equals(after)) issues.add("目标动作尚未替换");
            } else if (!before.equals(after)) {
                issues.add("替换时修改了其他动作");
            }
        }
    }

    private WorkoutDraftResponse normalize(WorkoutDraftResponse draft, LocalDate date,
                                           GenerationContext context,
                                           BigDecimal weight,
                                           int correctionCount,
                                           String userRequest,
                                           List<String> sessionExcludedCodes,
                                           WorkoutDraftResponse previousDraft) {
        TrainingPreferenceResponse preferences = context.preferences();
        Map<String, ExerciseDatabase> allowed = candidateMap(context.candidates());
        Map<String, WorkoutDraftResponse.ExerciseDraft> previousByCode = new LinkedHashMap<>();
        if (previousDraft != null && previousDraft.exercises() != null) {
            previousDraft.exercises().forEach(item -> previousByCode.put(item.exerciseCode(), item));
        }
        List<WorkoutCalorieFormula.Input> calorieInputs = draft.exercises().stream().map(item -> {
            ExerciseDatabase reference = allowed.get(item.exerciseCode());
            return new WorkoutCalorieFormula.Input(reference.getMet(),
                    draftSets(item, previousByCode.get(item.exerciseCode())));
        }).toList();
        List<BigDecimal> calorieValues = WorkoutCalorieFormula.allocate(
                weight, preferences.sessionMinutes(), calorieInputs);
        List<WorkoutDraftResponse.ExerciseDraft> exercises = new ArrayList<>();
        for (int index = 0; index < draft.exercises().size(); index++) {
            WorkoutDraftResponse.ExerciseDraft item = draft.exercises().get(index);
            ExerciseDatabase reference = allowed.get(item.exerciseCode());
            int sets = draftSets(item, previousByCode.get(item.exerciseCode()));
            BigDecimal calories = calorieValues.get(index);
            WeightRecommendation load = weightRecommendation(
                    item, previousByCode.get(item.exerciseCode()), reference,
                    context.recentWeights().get(reference.getName()));
            exercises.add(new WorkoutDraftResponse.ExerciseDraft(
                    reference.getExerciseCode(), reference.getName(), reference.getEquipment(),
                    reference.getPrimaryMuscles(), sets, item.reps(), item.durationSeconds(),
                    item.restSeconds(), item.rpe(), load.weight(), load.loadType(), load.source(), load.note(),
                    calories, item.rationale()));
        }
        int minutes = preferences.sessionMinutes();
        int targetRpe = draft.targetRpe() == null
                ? (preferences.targetRpeMin() + preferences.targetRpeMax()) / 2
                : Math.min(preferences.targetRpeMax(), Math.max(preferences.targetRpeMin(), draft.targetRpe()));
        String reply = draft.assistantReply() == null || draft.assistantReply().isBlank()
                ? "我已按你这次的需求，结合器械、动作偏好和身体限制生成方案。"
                : draft.assistantReply();
        return new WorkoutDraftResponse(draft.requestId(), draft.status(), draft.provider(), draftTitle(draft, context.intent()),
                reply, userRequest, date, minutes, targetRpe, exercises,
                draft.warnings() == null ? List.of() : draft.warnings(),
                draft.disclaimer(), correctionCount, null,
                sessionExcludedCodes == null ? List.of() : List.copyOf(sessionExcludedCodes));
    }

    /** 新动作统一从4组开始；已有草稿中的合法组数视为用户调整并继续保留。 */
    private int draftSets(WorkoutDraftResponse.ExerciseDraft generated,
                          WorkoutDraftResponse.ExerciseDraft previous) {
        if (previous != null && previous.sets() != null && previous.sets() >= 1 && previous.sets() <= 10) {
            return previous.sets();
        }
        return DEFAULT_DRAFT_SETS;
    }

    private WorkoutDraftResponse withNutritionPlan(WorkoutDraftResponse draft, Long userId, LocalDate date,
                                                    TrainingPreferenceResponse preferences) {
        User user = userService.findById(userId);
        BigDecimal target = BigDecimal.valueOf(userService.calculateDailyCalories(user))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal consumed = BigDecimal.valueOf(compareService.calculateDailyIntake(userId, date))
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal remaining = target.subtract(consumed).max(BigDecimal.ZERO);
        List<SystemRecipeTemplate> catalog = recipeTemplateMapper.searchEnabled(
                "", nutritionGoal(preferences.trainingGoal()), "", 0, 100);
        List<SystemRecipeTemplate> selected = selectMealTemplates(catalog);
        List<WorkoutDraftResponse.MealDraft> meals = selected.stream().map(item ->
                new WorkoutDraftResponse.MealDraft(mealSlot(item.getCategory()), item.getRecipeId(),
                        item.getMealType(), item.getCalorie(), item.getProtein(), item.getCarb(), item.getFat(),
                        item.getServingDescription(), item.getAllergenInfo())).toList();
        BigDecimal plannedCalories = selected.stream().map(SystemRecipeTemplate::getCalorie)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(0, RoundingMode.HALF_UP);
        BigDecimal plannedProtein = selected.stream().map(SystemRecipeTemplate::getProtein)
                .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(1, RoundingMode.HALF_UP);
        var nutrition = new WorkoutDraftResponse.NutritionPlan(target, consumed, remaining,
                plannedCalories, plannedProtein, meals,
                "这是基于目标与系统菜谱生成的建议餐单；确认训练不会把它记成已摄入。食物过敏请以餐单提示为准。");
        return new WorkoutDraftResponse(draft.requestId(), draft.status(), draft.provider(), draft.title(),
                draft.assistantReply(), draft.userRequest(), draft.date(), draft.estimatedDurationMinutes(),
                draft.targetRpe(), draft.exercises(), draft.warnings(), draft.disclaimer(),
                draft.correctionCount(), nutrition, draft.sessionExcludedExerciseCodes());
    }

    private List<String> sessionExclusions(WorkoutDraftResponse draft) {
        return draft == null || draft.sessionExcludedExerciseCodes() == null
                ? List.of() : draft.sessionExcludedExerciseCodes();
    }

    private List<SystemRecipeTemplate> selectMealTemplates(List<SystemRecipeTemplate> catalog) {
        List<SystemRecipeTemplate> selected = new ArrayList<>();
        for (String slot : List.of("早餐", "午餐", "晚餐")) {
            catalog.stream().filter(item -> item.getCategory() != null && item.getCategory().contains(slot))
                    .filter(item -> !selected.contains(item)).findFirst().ifPresent(selected::add);
        }
        catalog.stream().filter(item -> !selected.contains(item)).limit(3 - selected.size()).forEach(selected::add);
        return selected;
    }

    private String mealSlot(String category) {
        if (category == null) return "正餐";
        if (category.contains("早餐")) return "早餐";
        if (category.contains("午餐")) return "午餐";
        if (category.contains("晚餐")) return "晚餐";
        return "加餐";
    }

    private String nutritionGoal(String trainingGoal) {
        return switch (trainingGoal == null ? "" : trainingGoal) {
            case "FAT_LOSS" -> "减脂友好";
            case "MUSCLE_GAIN", "STRENGTH" -> "增肌友好";
            default -> "保持";
        };
    }

    private TrainingPreferenceResponse effectivePreferences(TrainingPreferenceResponse value,
                                                            WorkoutDraftAgentRequest.TrainingIntent intent) {
        return new TrainingPreferenceResponse(value.experienceLevel(), value.weeklyDays(),
                intent.maximumDurationMinutes(),
                value.trainingLocation(), value.trainingGoal(), value.equipmentTags(), value.limitationTags(),
                value.preferredExerciseCodes(), value.excludedExerciseCodes(), value.targetRpeMin(),
                value.targetRpeMax(), value.updatedAt());
    }

    private WorkoutDraftAgentRequest.TrainingIntent parseIntent(String text, int defaultMinutes) {
        String normalizedText = text == null ? "" : text;
        DurationRange duration = durationRange(normalizedText, defaultMinutes);
        List<DetectedRegion> detected = new ArrayList<>();
        List<String> excludedRegions = new ArrayList<>();
        for (RegionDefinition definition : regionDefinitions()) {
            int lastPositive = -1;
            int lastExcluded = -1;
            for (String keyword : definition.keywords()) {
                int start = normalizedText.indexOf(keyword);
                while (start >= 0) {
                    if (isNegatedRegionMention(normalizedText, start)) {
                        lastExcluded = Math.max(lastExcluded, start);
                    } else {
                        lastPositive = Math.max(lastPositive, start);
                    }
                    start = normalizedText.indexOf(keyword, start + keyword.length());
                }
            }
            // 对话会把本轮原句放在历史整理之后；最后一次明确表述决定该部位是目标还是排除项。
            if (lastExcluded > lastPositive) excludedRegions.add(definition.region());
            if (lastPositive >= 0 && lastPositive > lastExcluded) {
                detected.add(new DetectedRegion(definition, lastPositive));
            }
        }
        detected.sort((left, right) -> Integer.compare(left.position(), right.position()));
        List<WorkoutDraftAgentRequest.FocusRequirement> requirements = detected.stream().map(item -> {
            boolean exactlyOne = requestsExactlyOne(normalizedText, item.definition().keywords());
            return new WorkoutDraftAgentRequest.FocusRequirement(
                    item.definition().region(), item.definition().label(), 1, exactlyOne ? 1 : null);
        }).toList();
        return new WorkoutDraftAgentRequest.TrainingIntent(
                duration.minimum(), duration.maximum(), requirements, List.copyOf(excludedRegions));
    }

    private boolean isNegatedRegionMention(String text, int mentionStart) {
        int clauseStart = Math.max(
                Math.max(text.lastIndexOf('，', Math.max(0, mentionStart - 1)),
                        text.lastIndexOf('。', Math.max(0, mentionStart - 1))),
                Math.max(text.lastIndexOf('；', Math.max(0, mentionStart - 1)),
                        text.lastIndexOf('\n', Math.max(0, mentionStart - 1))));
        String prefix = text.substring(Math.max(clauseStart + 1, mentionStart - 12), mentionStart)
                .replaceAll("\\s+", "");
        return NEGATED_REGION_PREFIX_PATTERN.matcher(prefix).matches();
    }

    private boolean regionExcluded(String region, List<String> excludedRegions) {
        return excludedRegions != null && excludedRegions.stream()
                .anyMatch(excluded -> regionMatches(excluded, region));
    }

    private String draftTitle(WorkoutDraftResponse draft, WorkoutDraftAgentRequest.TrainingIntent intent) {
        if (intent.focusRequirements().isEmpty()) return draft.title();
        return intent.focusRequirements().stream()
                .map(WorkoutDraftAgentRequest.FocusRequirement::label)
                .distinct()
                .collect(java.util.stream.Collectors.joining("与")) + "训练计划";
    }

    private DurationRange durationRange(String text, int defaultMinutes) {
        Matcher hourRange = HOUR_RANGE_PATTERN.matcher(text);
        if (hourRange.find()) {
            return normalizedRange(minutes(Double.parseDouble(hourRange.group(1))),
                    minutes(Double.parseDouble(hourRange.group(2))));
        }
        Matcher minuteRange = MINUTE_RANGE_PATTERN.matcher(text);
        if (minuteRange.find()) {
            return normalizedRange(Integer.parseInt(minuteRange.group(1)),
                    Integer.parseInt(minuteRange.group(2)));
        }
        Matcher minute = DURATION_PATTERN.matcher(text);
        if (minute.find()) return targetDuration(Integer.parseInt(minute.group(1)));
        Matcher hour = HOUR_PATTERN.matcher(text);
        if (hour.find()) return targetDuration(minutes(Double.parseDouble(hour.group(1))));
        return normalizedRange(null, defaultMinutes);
    }

    /** 单个时长表达为目标值，允许约 20% 的编排误差，但不能退化成明显过短的计划。 */
    private DurationRange targetDuration(int targetMinutes) {
        int maximum = Math.max(10, Math.min(180, targetMinutes));
        int minimum = Math.max(10, (int) Math.ceil(maximum * 0.8));
        return normalizedRange(minimum, maximum);
    }

    private DurationRange normalizedRange(Integer minimum, int maximum) {
        int normalizedMaximum = Math.max(10, Math.min(180, maximum));
        Integer normalizedMinimum = minimum == null ? null
                : Math.max(10, Math.min(normalizedMaximum, minimum));
        return new DurationRange(normalizedMinimum, normalizedMaximum);
    }

    private int minutes(double hours) {
        return (int) Math.round(hours * 60);
    }

    private boolean requestsExactlyOne(String text, List<String> keywords) {
        String alternatives = keywords.stream().map(Pattern::quote)
                .collect(java.util.stream.Collectors.joining("|"));
        // “生成一个练肩的训练计划”中的“一个”修饰计划，不代表只要一个肩部动作。
        Pattern pattern = Pattern.compile("(?:一个|1个)\\s*(?:练|针对)?[^，。；\\n]{0,5}(?:"
                + alternatives + ")[^，。；\\n]{0,8}动作");
        return pattern.matcher(text).find();
    }

    private int focusScore(ExerciseDatabase exercise, WorkoutDraftAgentRequest.TrainingIntent intent) {
        String region = primaryRegion(exercise);
        return (int) intent.focusRequirements().stream()
                .filter(requirement -> regionMatches(requirement.region(), region)).count();
    }

    private void validateFocusRequirements(WorkoutDraftResponse draft, Map<String, ExerciseDatabase> allowed,
                                           WorkoutDraftAgentRequest.TrainingIntent intent,
                                           List<String> issues) {
        if (!intent.focusRequirements().isEmpty()) {
            boolean hasUnrelated = draft.exercises().stream().map(item -> allowed.get(item.exerciseCode()))
                    .filter(java.util.Objects::nonNull)
                    .map(this::primaryRegion)
                    .anyMatch(region -> intent.focusRequirements().stream()
                            .noneMatch(requirement -> regionMatches(requirement.region(), region)));
            if (hasUnrelated) issues.add("包含非本次目标部位的主训练动作");
        }
        for (WorkoutDraftAgentRequest.FocusRequirement requirement : intent.focusRequirements()) {
            long count = draft.exercises().stream().map(item -> allowed.get(item.exerciseCode()))
                    .filter(java.util.Objects::nonNull)
                    .map(this::primaryRegion)
                    .filter(region -> regionMatches(requirement.region(), region))
                    .count();
            if (count < requirement.minimumExercises()) {
                issues.add("缺少" + requirement.label() + "主训练动作");
            }
            if (requirement.maximumExercises() != null && count > requirement.maximumExercises()) {
                issues.add(requirement.label() + "动作数量超过本次要求");
            }
        }
        boolean hasExcludedRegion = draft.exercises().stream().map(item -> allowed.get(item.exerciseCode()))
                .filter(java.util.Objects::nonNull)
                .map(this::primaryRegion)
                .anyMatch(region -> regionExcluded(region, intent.excludedRegions()));
        if (hasExcludedRegion) issues.add("包含本次明确排除部位的动作");
    }

    private String primaryRegion(ExerciseDatabase exercise) {
        if (exercise == null || exercise.getPrimaryMuscles() == null) return "OTHER";
        String primary = exercise.getPrimaryMuscles().split(",", 2)[0].trim();
        if (primary.contains("胸")) return "CHEST";
        if (primary.contains("背")) return "BACK";
        if (primary.contains("肩") || primary.contains("三角")) return "SHOULDERS";
        if (primary.contains("腹") || primary.contains("核心")) return "CORE";
        if (primary.contains("臀")) return "GLUTES";
        if (primary.contains("股") || primary.contains("腿") || primary.contains("腘绳")
                || primary.contains("小腿")) return "LEGS";
        if (primary.contains("肱二头")) return "BICEPS";
        if (primary.contains("肱三头")) return "TRICEPS";
        return "OTHER";
    }

    private boolean regionMatches(String required, String actual) {
        if ("ARMS".equals(required)) return "BICEPS".equals(actual) || "TRICEPS".equals(actual);
        return required.equals(actual);
    }

    private List<RegionDefinition> regionDefinitions() {
        return List.of(
                new RegionDefinition("CHEST", "胸部",
                        List.of("胸部", "练胸", "胸肌", "胸背", "胸和背", "胸肩", "胸和肩")),
                new RegionDefinition("BACK", "背部",
                        List.of("背部", "练背", "背肌", "后背", "胸背", "胸和背", "肩背", "肩和背")),
                new RegionDefinition("SHOULDERS", "肩部",
                        List.of("肩膀", "肩部", "练肩", "三角肌", "胸肩", "胸和肩", "肩背", "肩和背")),
                new RegionDefinition("CORE", "腹部", List.of("腹部", "练腹", "核心", "腹肌")),
                new RegionDefinition("GLUTES", "臀部", List.of("臀部", "练臀", "臀肌")),
                new RegionDefinition("LEGS", "腿部", List.of("腿部", "练腿", "下肢")),
                new RegionDefinition("BICEPS", "肱二头肌", List.of("肱二头", "二头")),
                new RegionDefinition("TRICEPS", "肱三头肌", List.of("肱三头", "三头")),
                new RegionDefinition("ARMS", "手臂", List.of("手臂")));
    }

    private boolean temporarilyExcluded(ExerciseDatabase exercise, String userRequest) {
        if (userRequest == null || exercise.getName() == null) return false;
        String name = exercise.getName();
        for (String prefix : List.of("不要", "不做", "排除", "别安排")) {
            int start = userRequest.indexOf(prefix);
            while (start >= 0) {
                String clause = userRequest.substring(start + prefix.length()).split("[，。；,;\\n]", 2)[0];
                for (String token : clause.split("[和、/\\s]")) {
                    if (token.length() >= 2 && (name.contains(token) || token.contains(name))) return true;
                }
                start = userRequest.indexOf(prefix, start + prefix.length());
            }
        }
        return false;
    }

    private String normalizeUserRequest(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("请告诉助手今天想怎么练");
        String normalized = value.strip();
        if (normalized.length() > 500) throw new IllegalArgumentException("训练需求最多500字");
        return normalized;
    }

    private Map<String, ExerciseDatabase> candidateMap(List<ExerciseDatabase> candidates) {
        Map<String, ExerciseDatabase> result = new LinkedHashMap<>();
        candidates.forEach(item -> result.put(item.getExerciseCode(), item));
        return result;
    }

    private BigDecimal userWeight(Long userId) {
        User user = userService.findById(userId);
        return user == null || user.getWeight() == null ? BigDecimal.valueOf(70) : user.getWeight();
    }

    private void validateDate(LocalDate date) {
        if (date == null) throw new IllegalArgumentException("请选择训练日期");
        LocalDate today = LocalDate.now(ZoneId.of(businessZone));
        if (date.isBefore(today)) throw new IllegalArgumentException("AI只能生成今天或未来训练计划");
        if (date.isAfter(today.plusDays(90))) throw new IllegalArgumentException("训练计划最多可安排未来90天");
    }

    private record GenerationContext(
            TrainingPreferenceResponse preferences,
            List<ExerciseDatabase> candidates,
            WorkoutDraftAgentRequest.TrainingIntent intent,
            Map<String, BigDecimal> recentWeights) {}

    private record WeightRecommendation(
            BigDecimal weight, String loadType, String source, String note) {}

    private record DurationRange(Integer minimum, Integer maximum) {}

    private record RegionDefinition(String region, String label, List<String> keywords) {}

    private record DetectedRegion(RegionDefinition definition, int position) {}
}
