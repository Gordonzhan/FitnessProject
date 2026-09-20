package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.service.DatabaseService;
import com.fitness.service.RecipeService;
import com.fitness.service.WorkoutService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ControllerValidationTest {
    private final GlobalExceptionHandler advice = new GlobalExceptionHandler();

    @Test
    void invalidRecipeIsRejectedBeforeServiceCall() throws Exception {
        RecipeService recipes = mock(RecipeService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RecipeController(recipes))
                .setControllerAdvice(advice).build();

        mvc.perform(post("/recipe/save").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"recipeId\":\"ok\",\"name\":\"\",\"protein\":-1,\"carb\":0,\"fat\":0,\"calorie\":0,\"ingredients\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(recipes);
    }

    @Test
    void incompleteWorkoutRowIsRejectedBeforeServiceCall() throws Exception {
        WorkoutService workouts = mock(WorkoutService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkoutController(workouts))
                .setControllerAdvice(advice).build();

        mvc.perform(post("/workout/save").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"workoutId\":\"ok\",\"date\":\"2026-09-05\",\"totalCalories\":10,\"exercises\":[{\"name\":\"\",\"sets\":0,\"reps\":10,\"calories\":0}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(workouts);
    }

    @Test
    void negativeActualCaloriesCannotCompleteAWorkout() throws Exception {
        WorkoutService workouts = mock(WorkoutService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkoutController(workouts))
                .setControllerAdvice(advice).build();

        mvc.perform(post("/workout/complete/test-workout")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"actualCalories\":-1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(workouts);
    }

    @Test
    void invalidSearchPageSizeIsRejected() throws Exception {
        DatabaseService database = mock(DatabaseService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DatabaseController(database))
                .setControllerAdvice(advice).build();

        mvc.perform(get("/database/food/search").param("size", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(database);
    }

    @Test
    void invalidRecipeSourceIsRejectedBeforeServiceCall() throws Exception {
        RecipeService recipes = mock(RecipeService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new RecipeController(recipes))
                .setControllerAdvice(advice).build();

        mvc.perform(get("/recipe/catalog").param("source", "ADMIN")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(recipes);
    }

    @Test
    void impossibleWorkoutDateIsAClientError() throws Exception {
        WorkoutService workouts = mock(WorkoutService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new WorkoutController(workouts))
                .setControllerAdvice(advice).build();

        mvc.perform(get("/workout/get/2026-99-99")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("日期格式不正确，请使用真实的 yyyy-MM-dd 日期"));
        verifyNoInteractions(workouts);
    }

    @Test
    void missingFoodUsesHttpNotFound() throws Exception {
        DatabaseService database = mock(DatabaseService.class);
        when(database.getFoodByName("不存在食材")).thenReturn(null);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DatabaseController(database))
                .setControllerAdvice(advice).build();

        mvc.perform(get("/database/food/get/不存在食材"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("食材不存在"));
    }
}
