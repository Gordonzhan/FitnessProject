package com.fitness.repository;

import com.fitness.entity.ExerciseDatabase;
import com.fitness.entity.FoodDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
class ReferenceDatabaseRepositoryTest {
    @Autowired
    private FoodDatabaseRepository foodRepository;
    @Autowired
    private ExerciseDatabaseRepository exerciseRepository;

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
        exerciseRepository.save(exercise);

        assertEquals("深蹲", exerciseRepository.searchEnabled("臀部", "力量训练", PageRequest.of(0, 20))
                .getContent().get(0).getName());
        assertEquals("力量训练", exerciseRepository.findEnabledCategories().get(0));
    }
}
