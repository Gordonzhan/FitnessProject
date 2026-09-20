package com.fitness.repository;

import com.fitness.entity.FoodDatabase;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

@Repository
public interface FoodDatabaseRepository extends JpaRepository<FoodDatabase, Long> {
    FoodDatabase findByFoodName(String foodName);
    List<FoodDatabase> findAllByOrderByFoodNameAsc();

    @Query("select f from FoodDatabase f where f.enabled = true " +
            "and (:category = '' or f.category = :category) and " +
            "(:keyword = '' or f.foodName like concat('%', :keyword, '%') " +
            "or coalesce(f.aliases, '') like concat('%', :keyword, '%') " +
            "or f.category like concat('%', :keyword, '%')) " +
            "order by f.sortOrder desc, f.foodName asc")
    Page<FoodDatabase> searchEnabled(@Param("keyword") String keyword,
                                     @Param("category") String category,
                                     Pageable pageable);

    @Query("select distinct f.category from FoodDatabase f where f.enabled = true order by f.category")
    List<String> findEnabledCategories();

    List<FoodDatabase> findByFoodNameInAndEnabledTrue(List<String> foodNames);
}
