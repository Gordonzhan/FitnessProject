package com.fitness.service;

import com.fitness.common.BusinessException;
import com.fitness.entity.*;
import com.fitness.service.impl.OperationLogServiceImpl;
import com.fitness.service.impl.RecipeServiceImpl;
import com.fitness.service.impl.WorkoutServiceImpl;
import com.fitness.util.OSSUtil;
import jakarta.persistence.EntityManagerFactory;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// 不给测试类加 @Transactional：每次必须由实际 Service 代理负责提交/回滚，
// 否则测试事务会掩盖生产 Service 漏加事务的问题。
@SpringJUnitConfig(BusinessTransactionTest.Config.class)
public class BusinessTransactionTest {
    @Configuration
    @EnableTransactionManagement
    @MapperScan("com.fitness.mapper")
    @Import({WorkoutServiceImpl.class, RecipeServiceImpl.class, CompareService.class,
            ImageService.class, OperationLogServiceImpl.class})
    static class Config {
        @Bean DataSource dataSource() {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL("jdbc:h2:mem:tx_" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=DATE");
            ds.setUser("sa");
            new ResourceDatabasePopulator(new ClassPathResource("transaction-schema.sql")).execute(ds);
            // H2 缺少 MySQL 的 SUBSTRING_INDEX，保持生产 Mapper 原样，用等价测试函数补齐。
            new JdbcTemplate(ds).execute("CREATE ALIAS SUBSTRING_INDEX FOR 'com.fitness.service.BusinessTransactionTest.substringIndex'");
            return ds;
        }

        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(ds);
            factory.setPackagesToScan("com.fitness.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "none"));
            return factory;
        }

        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            // 与项目的 JPA/MyBatis 混用结构一致，验证两者共享同一事务连接。
            return new JpaTransactionManager(factory);
        }

        @Bean SqlSessionFactory sqlSessionFactory(DataSource ds) throws Exception {
            var factory = new SqlSessionFactoryBean();
            factory.setDataSource(ds);
            factory.setTypeAliasesPackage("com.fitness.entity");
            factory.setMapperLocations(new PathMatchingResourcePatternResolver().getResources("classpath:mapper/*.xml"));
            var configuration = new org.apache.ibatis.session.Configuration();
            configuration.setMapUnderscoreToCamelCase(true);
            factory.setConfiguration(configuration);
            return factory.getObject();
        }

        @Bean OSSUtil ossUtil() { return mock(OSSUtil.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean JdbcTemplate jdbcTemplate(DataSource ds) { return new JdbcTemplate(ds); }
    }

    // 此测试仅使用 count=-1；公共方法供 H2 反射调用。
    public static String substringIndex(String value, String delimiter, int count) {
        if (value == null) return null;
        int index = value.lastIndexOf(delimiter);
        return index < 0 ? value : value.substring(index + delimiter.length());
    }

    @Autowired WorkoutService workouts;
    @Autowired RecipeService recipes;
    @Autowired CompareService compare;
    @Autowired OperationLogService operationLogs;
    @Autowired OSSUtil oss;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    private static final String IMAGE = "https://testbucket.oss-cn-beijing.aliyuncs.com/fitness-diary/images/1/977b707b-4444-41e3-aef9-9c58d0fa7b28.jpeg";

    @BeforeEach void resetDatabase() {
        for (String table : List.of("workout_delete_guard", "recipe_delete_guard", "daily_intake",
                "exercises", "workouts", "recipe_images", "ingredients", "recipes", "operation_logs",
                "system_recipe_ingredients", "system_recipe_templates")) {
            jdbc.update("DELETE FROM " + table);
        }
        reset(oss);
        when(oss.belongsToUser(anyString(), eq(1L))).thenReturn(true);
        when(oss.objectKey(anyString())).thenReturn("fitness-diary/images/test.jpeg");
    }

    private int count(String table) { return jdbc.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class); }

    private Workout workout(String id, String... names) {
        Workout workout = new Workout();
        workout.setWorkoutId(id);
        workout.setUserId(1L);
        workout.setDate(LocalDate.of(2026, 9, 5));
        workout.setTotalCalories(new BigDecimal("100"));
        workout.setExercises(Arrays.stream(names).map(name -> {
            Exercise exercise = new Exercise();
            exercise.setName(name);
            exercise.setWeight(BigDecimal.TEN);
            exercise.setSets(3);
            exercise.setReps(10);
            exercise.setCalories(BigDecimal.TEN);
            return exercise;
        }).toList());
        return workout;
    }

    private Recipe recipe(String id, String... foods) {
        Recipe recipe = new Recipe();
        recipe.setRecipeId(id);
        recipe.setUserId(1L);
        recipe.setMealType("测试菜谱");
        recipe.setSteps("原步骤");
        recipe.setProtein(BigDecimal.TEN);
        recipe.setCarb(BigDecimal.TEN);
        recipe.setFat(BigDecimal.TEN);
        recipe.setCalorie(new BigDecimal("300"));
        RecipeImage image = new RecipeImage();
        image.setImageUrl(IMAGE);
        image.setSortOrder(0);
        recipe.setImages(List.of(image));
        recipe.setIngredients(Arrays.stream(foods).map(name -> {
            Ingredient ingredient = new Ingredient();
            ingredient.setFoodName(name);
            ingredient.setWeight(BigDecimal.TEN);
            ingredient.setProtein(BigDecimal.ZERO);
            ingredient.setCarb(BigDecimal.ZERO);
            ingredient.setFat(BigDecimal.ZERO);
            ingredient.setCalorie(BigDecimal.ZERO);
            return ingredient;
        }).toList());
        return recipe;
    }

    @Test void workoutSuccessCommitsCreateReplaceAndDelete() {
        workouts.saveWorkout(workout("success", "卧推", "深蹲"));
        assertEquals(1, count("workouts"));
        assertEquals(2, count("exercises"));
        workouts.saveWorkout(workout("success", "硬拉"));
        assertEquals(1, count("workouts"));
        assertEquals(List.of("硬拉"), jdbc.queryForList("SELECT name FROM exercises", String.class));
        assertTrue(workouts.deleteWorkoutByWorkoutId(1L, "success"));
        assertEquals(0, count("workouts"));
        assertEquals(0, count("exercises"));
    }

    @Test void listQueriesBatchHydrateRecipeAndWorkoutChildren() {
        recipes.saveRecipe(recipe("list-one", "鸡胸肉", "米饭"));
        recipes.saveRecipe(recipe("list-two", "牛肉"));
        Workout firstWorkout = workout("workout-one", "卧推", "深蹲");
        workouts.saveWorkout(firstWorkout);
        Workout secondWorkout = workout("workout-two", "硬拉");
        secondWorkout.setDate(LocalDate.of(2026, 9, 6));
        workouts.saveWorkout(secondWorkout);

        List<Recipe> recipeList = recipes.getRecipesByUserId(1L);
        List<Workout> workoutList = workouts.getWorkoutsByUserId(1L);

        assertEquals(2, recipeList.size());
        assertEquals(3, recipeList.stream().mapToInt(item -> item.getIngredients().size()).sum());
        assertEquals(2, recipeList.stream().mapToInt(item -> item.getImages().size()).sum());
        assertEquals(2, workoutList.size());
        assertEquals(3, workoutList.stream().mapToInt(item -> item.getExercises().size()).sum());
    }

    @Test void oneUserHasOnlyOneWorkoutPerDateAndLatestCompleteSaveWins() {
        workouts.saveWorkout(workout("first-id", "卧推", "深蹲"));
        Workout replacement = workout("second-id", "硬拉");
        replacement.setTotalCalories(new BigDecimal("250"));
        workouts.saveWorkout(replacement);

        assertEquals(1, count("workouts"));
        assertEquals("second-id", jdbc.queryForObject("SELECT workout_id FROM workouts", String.class));
        assertEquals(List.of("硬拉"), jdbc.queryForList("SELECT name FROM exercises", String.class));
        assertEquals(0, new BigDecimal("250").compareTo(
                jdbc.queryForObject("SELECT total_calories FROM workouts", BigDecimal.class)));
    }

    @Test void futurePlanDoesNotCountAsExpenditureAndCannotCompleteEarly() {
        Workout plan = workout("future-plan", "深蹲");
        plan.setDate(LocalDate.now().plusDays(1));
        plan.setStatus(WorkoutStatus.PLANNED);
        workouts.saveWorkout(plan);

        Workout stored = workouts.getWorkoutByDate(1L, plan.getDate());
        assertEquals(WorkoutStatus.PLANNED, stored.getStatus());
        assertEquals(0, new BigDecimal("100").compareTo(stored.getEstimatedCalories()));
        assertNull(stored.getActualCalories());
        assertEquals(0, compare.calculateDailyExpenditure(1L, plan.getDate()), 0.001);
        assertThrows(BusinessException.class,
                () -> workouts.completeWorkout(1L, "future-plan", new BigDecimal("90")));
        assertEquals(1, count("workouts"));
    }

    @Test void duePlanBecomesCompletedInPlaceAndCountsActualCaloriesOnce() {
        Workout plan = workout("due-plan", "卧推");
        plan.setDate(LocalDate.now());
        plan.setStatus(WorkoutStatus.PLANNED);
        workouts.saveWorkout(plan);
        assertEquals(0, compare.calculateDailyExpenditure(1L, plan.getDate()), 0.001);

        Workout completed = workouts.completeWorkout(1L, "due-plan", new BigDecimal("88.5"));
        assertEquals(WorkoutStatus.COMPLETED, completed.getStatus());
        assertEquals(0, new BigDecimal("88.5").compareTo(completed.getActualCalories()));
        assertEquals(88.5, compare.calculateDailyExpenditure(1L, plan.getDate()), 0.001);
        workouts.completeWorkout(1L, "due-plan", new BigDecimal("999"));
        assertEquals(1, count("workouts"));
        assertEquals(88.5, compare.calculateDailyExpenditure(1L, plan.getDate()), 0.001);
    }

    @Test void cancelledPlanIsRetainedButNeverCountsAsExpenditure() {
        Workout plan = workout("cancel-plan", "硬拉");
        plan.setDate(LocalDate.now().plusDays(2));
        plan.setStatus(WorkoutStatus.PLANNED);
        workouts.saveWorkout(plan);

        Workout cancelled = workouts.cancelWorkout(1L, "cancel-plan");
        assertEquals(WorkoutStatus.CANCELLED, cancelled.getStatus());
        assertNull(cancelled.getActualCalories());
        assertEquals(0, compare.calculateDailyExpenditure(1L, plan.getDate()), 0.001);
        assertEquals(1, count("workouts"));
        assertThrows(BusinessException.class,
                () -> workouts.completeWorkout(1L, "cancel-plan", new BigDecimal("100")));
    }

    @Test void invalidPlanDatesAndStatusAreRejectedBeforeWriting() {
        Workout tooFar = workout("too-far", "深蹲");
        tooFar.setDate(LocalDate.now().plusDays(91));
        tooFar.setStatus(WorkoutStatus.PLANNED);
        assertThrows(IllegalArgumentException.class, () -> workouts.saveWorkout(tooFar));

        Workout futureCompleted = workout("future-completed", "深蹲");
        futureCompleted.setDate(LocalDate.now().plusDays(1));
        futureCompleted.setStatus(WorkoutStatus.COMPLETED);
        assertThrows(IllegalArgumentException.class, () -> workouts.saveWorkout(futureCompleted));

        Workout historicalPlan = workout("historical-plan", "深蹲");
        historicalPlan.setDate(LocalDate.now().minusDays(1));
        historicalPlan.setStatus(WorkoutStatus.PLANNED);
        assertThrows(IllegalArgumentException.class, () -> workouts.saveWorkout(historicalPlan));
        assertEquals(0, count("workouts"));
    }

    @Test void workoutCreateFailureOnSecondExerciseRollsBackParentAndFirstChild() {
        assertThrows(RuntimeException.class, () -> workouts.saveWorkout(workout("new-fail", "卧推", null)));
        assertEquals(0, count("workouts"));
        assertEquals(0, count("exercises"));
    }

    @Test void workoutEditFailureRestoresOldTotalsAndAllOldExercises() {
        workouts.saveWorkout(workout("edit-fail", "卧推", "深蹲"));
        Workout edited = workout("edit-fail", "硬拉", null);
        edited.setTotalCalories(new BigDecimal("999"));
        assertThrows(RuntimeException.class, () -> workouts.saveWorkout(edited));
        assertEquals(0, new BigDecimal("100").compareTo(jdbc.queryForObject("SELECT total_calories FROM workouts", BigDecimal.class)));
        assertEquals(List.of("卧推", "深蹲"), jdbc.queryForList("SELECT name FROM exercises ORDER BY id", String.class));
    }

    @Test void workoutParentDeleteFailureRestoresPreviouslyDeletedExercises() {
        Workout saved = workouts.saveWorkout(workout("delete-fail", "卧推", "深蹲"));
        jdbc.update("INSERT INTO workout_delete_guard VALUES (?)", saved.getId());
        assertThrows(RuntimeException.class, () -> workouts.deleteWorkoutByWorkoutId(1L, "delete-fail"));
        assertEquals(1, count("workouts"));
        assertEquals(2, count("exercises"));
    }

    @Test void deletingOtherUsersWorkoutDoesNotChangeAnything() {
        workouts.saveWorkout(workout("owned", "卧推"));
        assertFalse(workouts.deleteWorkoutByWorkoutId(2L, "owned"));
        assertEquals(1, count("workouts"));
        assertEquals(1, count("exercises"));
    }

    @Test void recipeCreateFailureRollsBackParentImagesAndEarlierIngredients() {
        assertThrows(RuntimeException.class, () -> recipes.saveRecipe(recipe("recipe-new-fail", "鸡胸肉", null)));
        assertEquals(0, count("recipes"));
        assertEquals(0, count("recipe_images"));
        assertEquals(0, count("ingredients"));
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void recipeEditFailureRestoresStepsImagesAndIngredients() {
        recipes.saveRecipe(recipe("recipe-edit-fail", "鸡胸肉", "米饭"));
        Recipe edited = recipe("recipe-edit-fail", "牛肉", null);
        edited.setSteps("不应该保留的新步骤");
        edited.setImages(List.of());
        assertThrows(RuntimeException.class, () -> recipes.saveRecipe(edited));
        assertEquals("原步骤", jdbc.queryForObject("SELECT steps FROM recipes", String.class));
        assertEquals(IMAGE, jdbc.queryForObject("SELECT image_url FROM recipe_images", String.class));
        assertEquals(List.of("鸡胸肉", "米饭"), jdbc.queryForList("SELECT food_name FROM ingredients ORDER BY id", String.class));
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void recipeParentDeleteFailureRestoresItsChildrenAndDoesNotDeleteOss() {
        Recipe saved = recipes.saveRecipe(recipe("recipe-delete-fail", "鸡胸肉"));
        jdbc.update("INSERT INTO recipe_delete_guard VALUES (?)", saved.getId());
        assertThrows(RuntimeException.class, () -> recipes.deleteRecipeByRecipeId(1L, "recipe-delete-fail"));
        assertEquals(1, count("recipes"));
        assertEquals(1, count("recipe_images"));
        assertEquals(1, count("ingredients"));
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void committedRecipeImageRemovalTriggersOssCleanup() {
        recipes.saveRecipe(recipe("recipe-success", "鸡胸肉"));
        Recipe edited = recipe("recipe-success", "米饭");
        edited.setImages(List.of());
        recipes.saveRecipe(edited);
        assertEquals(0, count("recipe_images"));
        verify(oss, timeout(2000)).deleteImage(IMAGE);
    }

    @Test void dailyIntakeCommitsAddAndRemoveWithoutChangingRecipe() {
        recipes.saveRecipe(recipe("meal", "鸡胸肉"));
        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "meal");
        assertEquals(1, count("daily_intake"));
        compare.removeRecipeFromDailyIntake(1L, date, "meal");
        assertEquals(0, count("daily_intake"));
        assertEquals(1, count("recipes"));
    }

    @Test void dailyIntakeKeepsNutritionSnapshotAfterRecipeEditAndDelete() {
        recipes.saveRecipe(recipe("snapshot-meal", "鸡胸肉"));
        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "snapshot-meal", "snapshot-request");

        Recipe edited = recipe("snapshot-meal", "米饭");
        edited.setMealType("修改后的菜谱");
        edited.setProtein(new BigDecimal("99"));
        edited.setCalorie(new BigDecimal("999"));
        recipes.saveRecipe(edited);

        assertEquals(300, compare.calculateDailyIntake(1L, date), 0.001);
        assertEquals("测试菜谱", compare.getSelectedRecipes(1L, date).get(0).mealType());
        assertTrue(recipes.deleteRecipeByRecipeId(1L, "snapshot-meal"));
        assertEquals(1, count("daily_intake"));
        assertNull(jdbc.queryForObject("SELECT recipe_id FROM daily_intake", Long.class));
        assertEquals(300, compare.calculateDailyIntake(1L, date), 0.001);
        assertEquals("snapshot-meal", compare.getSelectedRecipes(1L, date).get(0).recipeId());
    }

    @Test void repeatedDailyIntakeRequestIsIdempotentButNewRequestAddsAnotherServing() {
        recipes.saveRecipe(recipe("idempotent-meal", "鸡胸肉"));
        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "idempotent-meal", "same-request");
        compare.addRecipeToDailyIntake(1L, date, "idempotent-meal", "same-request");
        assertEquals(1, count("daily_intake"));
        assertEquals(300, compare.calculateDailyIntake(1L, date), 0.001);

        compare.addRecipeToDailyIntake(1L, date, "idempotent-meal", "another-request");
        assertEquals(2, count("daily_intake"));
        assertEquals(600, compare.calculateDailyIntake(1L, date), 0.001);
    }

    @Test void operationLogUsesIndependentTransactionWhenBusinessRollsBack() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            workouts.saveWorkout(workout("rollback-business", "卧推"));
            OperationLog log = new OperationLog();
            log.setInterfaceName("/api/workout/save");
            log.setMethod("POST");
            operationLogs.saveLog(log);
            status.setRollbackOnly();
        });
        assertEquals(0, count("workouts"));
        assertEquals(0, count("exercises"));
        assertEquals(1, count("operation_logs"));
    }

    @Test void removingOneDuplicateMealKeepsOtherServingAndRecipe() {
        recipes.saveRecipe(recipe("duplicate-meal", "鸡胸肉"));
        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "duplicate-meal");
        compare.addRecipeToDailyIntake(1L, date, "duplicate-meal");
        var selected = compare.getSelectedRecipes(1L, date);
        assertEquals(2, selected.size());
        assertNotEquals(selected.get(0).intakeId(), selected.get(1).intakeId());
        assertEquals(600, compare.calculateDailyIntake(1L, date), 0.001);
        compare.removeDailyIntake(1L, date, Long.valueOf(selected.get(0).intakeId()));
        assertEquals(1, count("daily_intake"));
        assertEquals(selected.get(1).intakeId(), compare.getSelectedRecipes(1L, date).get(0).intakeId());
        assertEquals(300, compare.calculateDailyIntake(1L, date), 0.001);
        assertEquals(1, count("recipes"));
        assertEquals(1, count("recipe_images"));
        assertEquals(1, count("ingredients"));
        verify(oss, never()).deleteImage(anyString());
    }

    @Test void removingMealCannotAffectAnotherUserOrAnotherDay() {
        recipes.saveRecipe(recipe("scoped-meal", "鸡胸肉"));
        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "scoped-meal");
        Long id = Long.valueOf(compare.getSelectedRecipes(1L, date).get(0).intakeId());
        compare.removeDailyIntake(2L, date, id);
        compare.removeDailyIntake(1L, date.plusDays(1), id);
        assertEquals(1, count("daily_intake"));
        assertEquals(300, compare.calculateDailyIntake(1L, date), 0.001);
    }

    @Test void repeatedMealRemovalIsSafeAndLastServingClearsCalories() {
        recipes.saveRecipe(recipe("last-meal", "鸡胸肉"));
        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "last-meal");
        Long id = Long.valueOf(compare.getSelectedRecipes(1L, date).get(0).intakeId());
        compare.removeDailyIntake(1L, date, id);
        compare.removeDailyIntake(1L, date, id);
        assertEquals(0, compare.calculateDailyIntake(1L, date), 0.001);
        assertTrue(compare.getSelectedRecipes(1L, date).isEmpty());
        assertEquals(1, count("recipes"));
    }

    @Test void catalogKeepsUserRecipesFirstAndSystemRecipeCanBeLoggedWithoutBeingMutable() {
        recipes.saveRecipe(recipe("mine", "鸡胸肉"));
        jdbc.update("INSERT INTO system_recipe_templates " +
                        "(template_id,name,category,serving_description,steps,protein,carb,fat,calorie,source_name,source_ref,data_version,enabled,sort_order) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                "sys_test", "系统测试餐", "午餐", "1人份", "拌匀", 30, 40, 10, 370,
                "PROJECT_CALCULATED", "FDC:test", "test-v1", true, 100);
        Long templateId = jdbc.queryForObject("SELECT id FROM system_recipe_templates WHERE template_id='sys_test'", Long.class);
        jdbc.update("UPDATE system_recipe_templates SET goal_tags='减脂友好', cuisine_type='中式家常', " +
                "cover_image_url='https://test/system-cover.jpeg' WHERE id=?", templateId);
        jdbc.update("INSERT INTO system_recipe_ingredients " +
                        "(template_id,food_name,weight,protein,carb,fat,calorie,source_name,source_ref,data_version,sort_order) " +
                        "VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                templateId, "鸡胸肉", 100, 30, 0, 5, 165, "USDA_FDC", "FDC:test", "test-v1", 1);

        var catalog = recipes.searchCatalog(1L, "", "", "", "", 0, 20);
        assertEquals(2, catalog.getTotal());
        assertEquals("USER", catalog.getItems().get(0).getSourceType());
        assertEquals("SYSTEM", catalog.getItems().get(1).getSourceType());
        assertFalse(catalog.getItems().get(1).isDeletable());
        assertEquals("鸡胸肉", catalog.getItems().get(1).getIngredients().get(0).getFoodName());
        assertEquals("https://test/system-cover.jpeg", catalog.getItems().get(1).getImages().get(0).getImageUrl());
        assertEquals("FDC:test", recipes.getCatalogItem(1L, "sys_test").getSourceRef());
        var filtered = recipes.searchCatalog(1L, "系统", "减脂友好", "", "", 0, 20);
        assertEquals(1, filtered.getTotal());
        assertEquals("SYSTEM", filtered.getItems().get(0).getSourceType());
        var chinese = recipes.searchCatalog(1L, "", "", "中式家常", "", 0, 20);
        assertEquals(1, chinese.getTotal());
        assertEquals("中式家常", chinese.getItems().get(0).getCuisineType());
        var mine = recipes.searchCatalog(1L, "", "", "", "USER", 0, 20);
        assertEquals(1, mine.getTotal());
        assertEquals("USER", mine.getItems().get(0).getSourceType());
        var systems = recipes.searchCatalog(1L, "", "", "", "SYSTEM", 0, 20);
        assertEquals(1, systems.getTotal());
        assertEquals("SYSTEM", systems.getItems().get(0).getSourceType());

        LocalDate date = LocalDate.of(2026, 9, 5);
        compare.addRecipeToDailyIntake(1L, date, "sys_test", "system-request");
        assertNull(jdbc.queryForObject("SELECT recipe_id FROM daily_intake", Long.class));
        assertEquals(370, compare.calculateDailyIntake(1L, date), 0.001);
        compare.removeRecipeFromDailyIntake(1L, date, "sys_test");
        assertEquals(0, count("daily_intake"));
        assertThrows(IllegalArgumentException.class, () -> recipes.saveRecipe(recipe("sys_test", "鸡胸肉")));
    }
}
