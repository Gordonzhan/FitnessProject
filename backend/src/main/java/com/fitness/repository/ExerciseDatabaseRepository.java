package com.fitness.repository;

import com.fitness.entity.ExerciseDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface ExerciseDatabaseRepository extends JpaRepository<ExerciseDatabase, Long> {
    ExerciseDatabase findByName(String name);
    List<ExerciseDatabase> findAllByOrderByNameAsc();

    @Query("select e from ExerciseDatabase e where e.enabled = true " +
            "and (:category = '' or e.category = :category) and " +
            "(:keyword = '' or e.name like concat('%', :keyword, '%') " +
            "or coalesce(e.aliases, '') like concat('%', :keyword, '%') " +
            "or e.category like concat('%', :keyword, '%') " +
            "or e.primaryMuscles like concat('%', :keyword, '%') " +
            "or e.equipment like concat('%', :keyword, '%')) " +
            "order by e.sortOrder desc, e.name asc")
    Page<ExerciseDatabase> searchEnabled(@Param("keyword") String keyword,
                                         @Param("category") String category,
                                         Pageable pageable);

    @Query("select distinct e.category from ExerciseDatabase e where e.enabled = true order by e.category")
    List<String> findEnabledCategories();

    List<ExerciseDatabase> findByNameInAndEnabledTrue(List<String> names);
}
