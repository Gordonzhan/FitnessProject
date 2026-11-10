package com.fitness.service;

import com.fitness.common.PageResult;
import com.fitness.dto.AnalysisSummary;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.FoodDatabase;
import com.fitness.entity.User;
import com.fitness.entity.Workout;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A7 只读工具适配层：所有查询都显式绑定已验证的当前 userId。 */
@Service
@Transactional(readOnly = true)
public class AgentReadToolService {
    private final UserService users;
    private final TrainingPreferenceService preferences;
    private final AnalysisService analysis;
    private final WorkoutService workouts;
    private final DatabaseService database;
    private final String businessZone;

    public AgentReadToolService(UserService users, TrainingPreferenceService preferences,
                                AnalysisService analysis, WorkoutService workouts, DatabaseService database,
                                @Value("${fitness.business-zone:Asia/Shanghai}") String businessZone) {
        this.users = users; this.preferences = preferences; this.analysis = analysis;
        this.workouts = workouts; this.database = database; this.businessZone = businessZone;
    }

    public Map<String, Object> profile(Long userId) {
        User user = users.findById(userId);
        if (user == null) throw new IllegalArgumentException("用户档案不存在");
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("gender", user.getGender()); value.put("age", user.getAge());
        value.put("heightCm", user.getHeight()); value.put("weightKg", user.getWeight());
        value.put("goal", user.getGoal()); value.put("activityLevel", user.getActivityLevel());
        value.put("dailyCalorieTarget", users.calculateDailyCalories(user));
        return value;
    }

    public Object preferences(Long userId) { return preferences.get(userId); }

    public Map<String, Object> recentAnalysis(Long userId, int days) {
        int bounded = days == 30 ? 30 : 7;
        LocalDate end = today();
        AnalysisSummary summary = analysis.analyze(userId, end.minusDays(bounded - 1L), end);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("startDate", summary.startDate()); value.put("endDate", summary.endDate());
        value.put("dataState", summary.dataState()); value.put("averages", summary.averages());
        value.put("trainingCompletion", summary.trainingCompletion());
        value.put("weightTrend", summary.weightPoints()); value.put("goalAssessment", summary.goalAssessment());
        value.put("notices", summary.notices());
        return value;
    }

    public Map<String, Object> todayNutrition(Long userId) {
        LocalDate today = today();
        AnalysisSummary summary = analysis.analyze(userId, today, today);
        AnalysisSummary.DailyPoint point = summary.dailyPoints().get(0);
        User user = users.findById(userId);
        if (user == null) throw new IllegalArgumentException("用户档案不存在");
        java.math.BigDecimal target = java.math.BigDecimal.valueOf(users.calculateDailyCalories(user));
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("date", today); value.put("recorded", point.hasNutrition());
        value.put("calories", point.intakeCalories()); value.put("protein", point.protein());
        value.put("carb", point.carb()); value.put("fat", point.fat());
        value.put("dailyTarget", target);
        value.put("remainingCalories", target.subtract(point.intakeCalories()).max(java.math.BigDecimal.ZERO));
        return value;
    }

    public Map<String, Object> recentWorkouts(Long userId, int days) {
        int bounded = days == 30 ? 30 : 7;
        LocalDate start = today().minusDays(bounded - 1L);
        List<Map<String, Object>> items = workouts.getWorkoutsByUserId(userId).stream()
                .filter(item -> !item.getDate().isBefore(start) && !item.getDate().isAfter(today()))
                .limit(30).map(this::workoutSummary).toList();
        return Map.of("days", bounded, "items", items);
    }

    public Map<String, Object> searchFoods(String keyword, int limit) {
        PageResult<FoodDatabase> page = database.searchFoods(keyword, "", 0, boundedLimit(limit));
        return Map.of("items", page.getItems().stream().map(item -> Map.of(
                "name", item.getFoodName(), "category", item.getCategory(),
                "servingState", item.getServingState(), "caloriesPer100g", item.getCalorie(),
                "proteinPer100g", item.getProtein(), "carbPer100g", item.getCarb(),
                "fatPer100g", item.getFat())).toList());
    }

    public Map<String, Object> searchExercises(String keyword, int limit) {
        PageResult<ExerciseDatabase> page = database.searchExercises(keyword, "", 0, boundedLimit(limit));
        return Map.of("items", page.getItems().stream().map(this::exerciseKnowledge).toList());
    }

    /** 动作目录是具体动作事实的权威来源；RAG 只补充可引用的通用动作模式知识。 */
    private Map<String, Object> exerciseKnowledge(ExerciseDatabase item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("exerciseCode", item.getExerciseCode());
        value.put("name", item.getName());
        value.put("category", item.getCategory());
        value.put("primaryMuscles", item.getPrimaryMuscles());
        value.put("equipment", item.getEquipment());
        value.put("intensity", item.getIntensity());
        value.put("instructions", text(item.getInstructions()));
        value.put("safetyCues", text(item.getSafetyCues()));
        value.put("commonMistakes", text(item.getCommonMistakes()));
        value.put("limitationTags", csv(item.getLimitationTags()));
        value.put("alternativeExerciseCodes", csv(item.getAlternativeExerciseCodes()));
        value.put("defaultSets", item.getDefaultSets());
        value.put("defaultReps", item.getDefaultReps());
        value.put("defaultRestSeconds", item.getDefaultRestSeconds());
        value.put("defaultRpe", item.getDefaultRpe());
        value.put("tempo", text(item.getTempo()));
        return value;
    }

    private Map<String, Object> workoutSummary(Workout item) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("date", item.getDate()); value.put("status", item.getStatus());
        value.put("estimatedCalories", item.getEstimatedCalories());
        value.put("actualCalories", item.getActualCalories());
        value.put("exercises", item.getExercises() == null ? List.of() : item.getExercises().stream()
                .map(exercise -> {
                    Map<String, Object> detail = new LinkedHashMap<>();
                    detail.put("name", exercise.getName()); detail.put("sets", exercise.getSets());
                    detail.put("reps", exercise.getReps()); return detail;
                }).toList());
        return value;
    }

    private int boundedLimit(Integer limit) { return Math.min(Math.max(limit == null ? 5 : limit, 1), 10); }
    private String text(String value) { return value == null ? "" : value; }
    private List<String> csv(String value) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isEmpty()).distinct().toList();
    }
    private LocalDate today() { return LocalDate.now(ZoneId.of(businessZone)); }
}
