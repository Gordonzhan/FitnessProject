package com.fitness.repository;

import com.fitness.entity.DailyIntake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface DailyIntakeRepository extends JpaRepository<DailyIntake, Long> {
    List<DailyIntake> findByUserIdAndDate(Long userId, LocalDate date);
    void deleteByUserIdAndDateAndRecipeId(Long userId, LocalDate date, Long recipeId);
}
