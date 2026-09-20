package com.fitness.service;

import com.fitness.entity.Exercise;
import com.fitness.entity.Ingredient;
import com.fitness.entity.Recipe;
import com.fitness.entity.RecipeImage;
import com.fitness.entity.Workout;
import com.fitness.mapper.ExerciseMapper;
import com.fitness.mapper.IngredientMapper;
import com.fitness.mapper.RecipeImageMapper;
import com.fitness.mapper.RecipeMapper;
import com.fitness.mapper.WorkoutMapper;
import com.fitness.service.impl.RecipeServiceImpl;
import com.fitness.service.impl.WorkoutServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListQueryOptimizationTest {
    @Mock RecipeMapper recipeMapper;
    @Mock RecipeImageMapper imageMapper;
    @Mock IngredientMapper ingredientMapper;
    @Mock ImageService imageService;
    @InjectMocks RecipeServiceImpl recipeService;

    @Mock WorkoutMapper workoutMapper;
    @Mock ExerciseMapper exerciseMapper;
    @InjectMocks WorkoutServiceImpl workoutService;

    @Test
    void recipeListLoadsAllChildrenWithTwoBatchQueries() {
        Recipe first = recipe(11L);
        Recipe second = recipe(12L);
        RecipeImage image = new RecipeImage();
        image.setRecipeDbId(11L);
        Ingredient ingredient = new Ingredient();
        ingredient.setRecipeDbId(12L);
        when(recipeMapper.findByUserId(1L)).thenReturn(List.of(first, second));
        when(imageMapper.findByRecipeDbIds(List.of(11L, 12L))).thenReturn(List.of(image));
        when(ingredientMapper.findByRecipeDbIds(List.of(11L, 12L))).thenReturn(List.of(ingredient));

        List<Recipe> result = recipeService.getRecipesByUserId(1L);

        assertEquals(List.of(image), result.get(0).getImages());
        assertTrue(result.get(0).getIngredients().isEmpty());
        assertTrue(result.get(1).getImages().isEmpty());
        assertEquals(List.of(ingredient), result.get(1).getIngredients());
        verify(imageMapper, never()).findByRecipeDbId(org.mockito.ArgumentMatchers.anyLong());
        verify(ingredientMapper, never()).findByRecipeDbId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void workoutListLoadsAllExercisesWithOneBatchQuery() {
        Workout first = workout(21L);
        Workout second = workout(22L);
        Exercise exercise = new Exercise();
        exercise.setWorkoutDbId(22L);
        when(workoutMapper.findByUserId(1L)).thenReturn(List.of(first, second));
        when(exerciseMapper.findByWorkoutDbIds(List.of(21L, 22L))).thenReturn(List.of(exercise));

        List<Workout> result = workoutService.getWorkoutsByUserId(1L);

        assertTrue(result.get(0).getExercises().isEmpty());
        assertEquals(List.of(exercise), result.get(1).getExercises());
        verify(exerciseMapper, never()).findByWorkoutDbId(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void emptyListsDoNotIssueChildQueries() {
        when(recipeMapper.findByUserId(1L)).thenReturn(List.of());
        when(workoutMapper.findByUserId(1L)).thenReturn(List.of());

        assertTrue(recipeService.getRecipesByUserId(1L).isEmpty());
        assertTrue(workoutService.getWorkoutsByUserId(1L).isEmpty());

        verify(imageMapper, never()).findByRecipeDbIds(org.mockito.ArgumentMatchers.anyList());
        verify(ingredientMapper, never()).findByRecipeDbIds(org.mockito.ArgumentMatchers.anyList());
        verify(exerciseMapper, never()).findByWorkoutDbIds(org.mockito.ArgumentMatchers.anyList());
    }

    private Recipe recipe(long id) {
        Recipe recipe = new Recipe();
        recipe.setId(id);
        return recipe;
    }

    private Workout workout(long id) {
        Workout workout = new Workout();
        workout.setId(id);
        return workout;
    }
}
