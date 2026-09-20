package com.fitness.service;

import com.fitness.entity.Recipe;
import com.fitness.entity.Workout;
import com.fitness.service.impl.RecipeServiceImpl;
import com.fitness.service.impl.WorkoutServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionPolicyTest {
    @Test void allAggregateWritesRollBackCheckedExceptionsToo() throws Exception {
        List<Method> writes = List.of(
                WorkoutServiceImpl.class.getMethod("saveWorkout", Workout.class),
                WorkoutServiceImpl.class.getMethod("deleteWorkoutByWorkoutId", Long.class, String.class),
                RecipeServiceImpl.class.getMethod("saveRecipe", Recipe.class),
                RecipeServiceImpl.class.getMethod("deleteRecipeByRecipeId", Long.class, String.class),
                CompareService.class.getMethod("addRecipeToDailyIntake", Long.class, LocalDate.class, String.class),
                CompareService.class.getMethod("addRecipeToDailyIntake", Long.class, LocalDate.class, String.class, String.class),
                CompareService.class.getMethod("removeRecipeFromDailyIntake", Long.class, LocalDate.class, String.class),
                CompareService.class.getMethod("removeDailyIntake", Long.class, LocalDate.class, Long.class));
        var source = new AnnotationTransactionAttributeSource();
        for (Method method : writes) {
            var attribute = source.getTransactionAttribute(method, method.getDeclaringClass());
            assertNotNull(attribute, method.toString());
            assertFalse(attribute.isReadOnly(), method.toString());
            assertTrue(attribute.rollbackOn(new Exception("checked")), method.toString());
            assertTrue(attribute.rollbackOn(new IllegalStateException("runtime")), method.toString());
        }
    }
}
