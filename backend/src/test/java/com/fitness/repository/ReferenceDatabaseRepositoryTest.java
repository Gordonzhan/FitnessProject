package com.fitness.repository;

import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.FoodDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.annotation.Import;
import com.fitness.service.DatabaseService;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@Import(DatabaseService.class)
class ReferenceDatabaseRepositoryTest {
    @Autowired
    private FoodDatabaseRepository foodRepository;
    @Autowired
    private ExerciseDatabaseRepository exerciseRepository;
    @Autowired
    private DatabaseService databaseService;

    @Test
    void foodSearchMatchesAliasAndPaginates() {
        FoodDatabase food = new FoodDatabase();
        food.setFoodName("西红柿");
        food.setAliases("番茄");
        food.setCategory("蔬菜");
        food.setServingState("可食部");
        food.setProtein(BigDecimal.ONE);
        food.setCarb(BigDecimal.ONE);
        food.setFat(BigDecimal.ONE);
        food.setCalorie(BigDecimal.TEN);
        food.setSourceName("TEST");
        food.setDataVersion("1");
        food.setEnabled(true);
        food.setSortOrder(1);
        foodRepository.save(food);

        assertEquals("西红柿", foodRepository.searchEnabled("番茄", "蔬菜", PageRequest.of(0, 20))
                .getContent().get(0).getFoodName());
        assertEquals(0, foodRepository.searchEnabled("番茄", "水果", PageRequest.of(0, 20)).getTotalElements());
        assertEquals("蔬菜", foodRepository.findEnabledCategories().get(0));
    }

    @Test
    void exerciseSearchMatchesMuscle() {
        ExerciseDatabase exercise = new ExerciseDatabase();
        exercise.setExerciseCode("EX_TEST_SQUAT");
        exercise.setName("深蹲");
        exercise.setAliases("杠铃深蹲");
        exercise.setCategory("力量训练");
        exercise.setPrimaryMuscles("腿部,臀部");
        exercise.setEquipment("杠铃");
        exercise.setIntensity("中等");
        exercise.setMet(new BigDecimal("5.0"));
        exercise.setSourceName("TEST");
        exercise.setDataVersion("1");
        exercise.setEnabled(true);
        exercise.setSortOrder(1);
        exercise.setMediaType("NONE");
        exercise.setFollowAlongEnabled(true);
        exerciseRepository.save(exercise);

        assertEquals("深蹲", exerciseRepository.searchEnabled("臀部", "力量训练", PageRequest.of(0, 20))
                .getContent().get(0).getName());
        assertEquals("力量训练", exerciseRepository.findEnabledCategories().get(0));
    }

    @Test
    void exerciseSearchFiltersEquipmentAndLimitationsBeforePagination() {
        exerciseRepository.save(exercise("安全哑铃动作", "EX_SAFE_DUMBBELL", "哑铃", null, 5));
        exerciseRepository.save(exercise("膝部哑铃动作", "EX_KNEE_DUMBBELL", "哑铃", "KNEE", 4));
        exerciseRepository.save(exercise("安全徒手动作", "EX_SAFE_BODYWEIGHT", "无", null, 3));
        exerciseRepository.save(exercise("杠铃动作", "EX_BARBELL", "杠铃", null, 2));

        var result = databaseService.searchExercises("", "力量训练", "哑铃", "KNEE", 0, 20);

        assertEquals(2, result.getTotal());
        assertEquals(java.util.Set.of("安全哑铃动作", "安全徒手动作"), result.getItems().stream()
                .map(ExerciseDatabase::getName).collect(java.util.stream.Collectors.toSet()));
    }

    private ExerciseDatabase exercise(String name, String code, String equipment, String limitations, int order) {
        ExerciseDatabase exercise = new ExerciseDatabase();
        exercise.setExerciseCode(code);
        exercise.setName(name);
        exercise.setCategory("力量训练");
        exercise.setPrimaryMuscles("全身");
        exercise.setEquipment(equipment);
        exercise.setIntensity("中等");
        exercise.setMet(new BigDecimal("5.0"));
        exercise.setSourceName("TEST");
        exercise.setDataVersion("1");
        exercise.setEnabled(true);
        exercise.setSortOrder(order);
        exercise.setLimitationTags(limitations);
        exercise.setMediaType("NONE");
        exercise.setFollowAlongEnabled(true);
        return exercise;
    }
}
