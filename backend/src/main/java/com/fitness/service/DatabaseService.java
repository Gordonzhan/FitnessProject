package com.fitness.service;

import com.fitness.entity.FoodDatabase;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.repository.FoodDatabaseRepository;
import com.fitness.repository.ExerciseDatabaseRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Value;

import com.fitness.common.PageResult;

import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
@Transactional(readOnly = true)
public class DatabaseService {
    @Autowired
    private FoodDatabaseRepository foodDatabaseRepository;
    @Autowired
    private ExerciseDatabaseRepository exerciseDatabaseRepository;
    @Value("${fitness.performance.category-cache-ttl-seconds:300}")
    private long categoryCacheTtlSeconds = 300;
    private final Object foodCategoryLock = new Object();
    private final Object exerciseCategoryLock = new Object();
    private volatile CategoryCache foodCategoryCache;
    private volatile CategoryCache exerciseCategoryCache;
    
    /** 查询完整食材库；仅供兼容旧接口，小程序选择器应优先使用分页搜索。 */
    public List<FoodDatabase> getAllFoods() {
        return foodDatabaseRepository.findAllByOrderByFoodNameAsc();
    }
    
    /** 根据标准名称精确查询一条食材数据。 */
    public FoodDatabase getFoodByName(String foodName) {
        return foodDatabaseRepository.findByFoodName(foodName);
    }
    
    /** 查询完整动作库；仅供兼容旧接口，小程序选择器应优先使用分页搜索。 */
    public List<ExerciseDatabase> getAllExercises() {
        return exerciseDatabaseRepository.findAllByOrderByNameAsc();
    }
    
    /** 根据标准名称精确查询一条训练动作。 */
    public ExerciseDatabase getExerciseByName(String name) {
        return exerciseDatabaseRepository.findByName(name);
    }

    /** 使用稳定业务编号查询可跟练动作详情。 */
    public ExerciseDatabase getExerciseByCode(String exerciseCode) {
        if (exerciseCode == null) return null;
        return exerciseDatabaseRepository.findByExerciseCodeAndEnabledTrue(exerciseCode.trim().toUpperCase());
    }

    /** 按名称、别名或分类分页搜索启用状态的食材。 */
    public PageResult<FoodDatabase> searchFoods(String keyword, String category, int page, int size) {
        Page<FoodDatabase> result = foodDatabaseRepository.searchEnabled(
                normalizeKeyword(keyword), normalizeCategory(category),
                PageRequest.of(normalizePage(page), normalizeSize(size)));
        return new PageResult<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
    }

    /** 按名称、别名、分类、肌群或器械分页搜索启用状态的动作。 */
    public PageResult<ExerciseDatabase> searchExercises(String keyword, String category, int page, int size) {
        return searchExercises(keyword, category, "", "", page, size);
    }

    /**
     * 按用户可用器械和限制标签在数据库层过滤，保持分页总数和结果一致。
     * limitationTags 使用稳定英文编码，避免把自由文本病史拼入查询。
     */
    public PageResult<ExerciseDatabase> searchExercises(String keyword, String category,
                                                        String equipment, String excludedLimitations,
                                                        int page, int size) {
        String normalizedKeyword = normalizeKeyword(keyword);
        String normalizedCategory = normalizeCategory(category);
        List<String> equipmentValues = normalizeCsv(equipment, 20, 50, false);
        List<String> limitationValues = normalizeCsv(excludedLimitations, 10, 30, true);
        if (!equipmentValues.isEmpty() && !equipmentValues.contains("无")) {
            equipmentValues = new java.util.ArrayList<>(equipmentValues);
            equipmentValues.add("无");
        }

        List<String> finalEquipmentValues = equipmentValues;
        Specification<ExerciseDatabase> specification = (root, query, cb) -> {
            List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
            predicates.add(cb.isTrue(root.get("enabled")));
            if (!normalizedCategory.isEmpty()) predicates.add(cb.equal(root.get("category"), normalizedCategory));
            if (!normalizedKeyword.isEmpty()) {
                String pattern = "%" + normalizedKeyword + "%";
                predicates.add(cb.or(cb.like(root.get("name"), pattern),
                        cb.like(cb.coalesce(root.get("aliases"), ""), pattern),
                        cb.like(root.get("category"), pattern),
                        cb.like(root.get("primaryMuscles"), pattern),
                        cb.like(root.get("equipment"), pattern)));
            }
            if (!finalEquipmentValues.isEmpty()) predicates.add(root.get("equipment").in(finalEquipmentValues));
            for (String limitation : limitationValues) {
                var padded = cb.concat(cb.concat(",", cb.coalesce(root.get("limitationTags"), "")), ",");
                predicates.add(cb.notLike(padded, "%," + limitation + ",%"));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        PageRequest pageable = PageRequest.of(normalizePage(page), normalizeSize(size),
                Sort.by(Sort.Order.desc("sortOrder"), Sort.Order.asc("name")));
        Page<ExerciseDatabase> result = exerciseDatabaseRepository.findAll(specification, pageable);
        return new PageResult<>(result.getContent(), result.getTotalElements(), result.getNumber(), result.getSize());
    }

    /** 批量解析已选食材名称，避免前端逐条请求营养数据。 */
    public List<FoodDatabase> resolveFoods(List<String> names) {
        List<String> normalized = normalizeNames(names);
        return normalized.isEmpty() ? List.of() : foodDatabaseRepository.findByFoodNameInAndEnabledTrue(normalized);
    }

    /** 批量解析已选动作名称，避免前端逐条请求动作数据。 */
    public List<ExerciseDatabase> resolveExercises(List<String> names) {
        List<String> normalized = normalizeNames(names);
        return normalized.isEmpty() ? List.of() : exerciseDatabaseRepository.findByNameInAndEnabledTrue(normalized);
    }

    /** 按稳定业务编号批量恢复动作详情，供训练偏好和后续草稿页面使用。 */
    public List<ExerciseDatabase> resolveExerciseCodes(List<String> exerciseCodes) {
        List<String> normalized = normalizeCodes(exerciseCodes);
        return normalized.isEmpty() ? List.of()
                : exerciseDatabaseRepository.findByExerciseCodeInAndEnabledTrue(normalized);
    }

    /** 查询启用食材的分类列表，并使用短时内存缓存降低数据库压力。 */
    public List<String> getFoodCategories() {
        CategoryCache cached = foodCategoryCache;
        long now = System.nanoTime();
        if (cached != null && cached.expiresAtNanos() > now) return cached.values();
        synchronized (foodCategoryLock) {
            cached = foodCategoryCache;
            if (cached != null && cached.expiresAtNanos() > now) return cached.values();
            List<String> values = List.copyOf(foodDatabaseRepository.findEnabledCategories());
            foodCategoryCache = new CategoryCache(values, expiresAt(now));
            return values;
        }
    }

    /** 查询启用动作的分类列表，并使用短时内存缓存降低数据库压力。 */
    public List<String> getExerciseCategories() {
        CategoryCache cached = exerciseCategoryCache;
        long now = System.nanoTime();
        if (cached != null && cached.expiresAtNanos() > now) return cached.values();
        synchronized (exerciseCategoryLock) {
            cached = exerciseCategoryCache;
            if (cached != null && cached.expiresAtNanos() > now) return cached.values();
            List<String> values = List.copyOf(exerciseDatabaseRepository.findEnabledCategories());
            exerciseCategoryCache = new CategoryCache(values, expiresAt(now));
            return values;
        }
    }
    
    /** 保存一条食材库数据，并使食材分类缓存失效。 */
    @Transactional
    public FoodDatabase saveFood(FoodDatabase food) {
        FoodDatabase saved = foodDatabaseRepository.save(food);
        foodCategoryCache = null;
        return saved;
    }
    
    /** 保存一条动作库数据，并使动作分类缓存失效。 */
    @Transactional
    public ExerciseDatabase saveExercise(ExerciseDatabase exercise) {
        ExerciseDatabase saved = exerciseDatabaseRepository.save(exercise);
        exerciseCategoryCache = null;
        return saved;
    }

    /** 清理并限制搜索关键字长度，避免异常查询参数放大数据库负担。 */
    private String normalizeKeyword(String keyword) {
        if (keyword == null) return "";
        String normalized = keyword.trim();
        return normalized.length() > 50 ? normalized.substring(0, 50) : normalized;
    }

    /** 清理并限制分类条件长度。 */
    private String normalizeCategory(String category) {
        if (category == null) return "";
        String normalized = category.trim();
        return normalized.length() > 30 ? normalized.substring(0, 30) : normalized;
    }

    /** 将页码限制为从零开始的合法值。 */
    private int normalizePage(int page) {
        return Math.max(page, 0);
    }

    /** 将每页条数限制在 1 到 50，防止一次加载过多数据。 */
    private int normalizeSize(int size) {
        return Math.min(Math.max(size, 1), 50);
    }

    /** 清理、去重并限制批量解析的名称数量。 */
    private List<String> normalizeNames(List<String> names) {
        if (names == null) return List.of();
        return names.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .limit(100)
                .toList();
    }

    private List<String> normalizeCodes(List<String> values) {
        if (values == null) return List.of();
        return values.stream().filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase()).distinct().limit(100).toList();
    }

    private List<String> normalizeCsv(String value, int maxItems, int maxLength, boolean uppercase) {
        if (value == null || value.isBlank()) return List.of();
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(item -> !item.isEmpty())
                .map(item -> item.length() > maxLength ? item.substring(0, maxLength) : item)
                .map(item -> uppercase ? item.toUpperCase() : item)
                .filter(item -> !uppercase || item.matches("[A-Z_]+"))
                .distinct().limit(maxItems).toList();
    }

    /** 根据当前单调时钟计算分类缓存失效时间。 */
    private long expiresAt(long now) {
        return now + TimeUnit.SECONDS.toNanos(Math.max(categoryCacheTtlSeconds, 0));
    }

    /** 分类列表及其基于单调时钟的失效时间。 */
    private record CategoryCache(List<String> values, long expiresAtNanos) {}
}
