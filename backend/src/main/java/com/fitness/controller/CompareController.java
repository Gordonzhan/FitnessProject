package com.fitness.controller;

import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.common.Response;
import com.fitness.dto.AddIntakeRequest;
import com.fitness.dto.DailyDataRequest;
import com.fitness.dto.RemoveIntakeRequest;
import com.fitness.dto.RemoveRecipeRequest;
import com.fitness.entity.User;
import com.fitness.entity.Workout;
import com.fitness.service.CompareService;
import com.fitness.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/compare")
public class CompareController {
    private final CompareService compareService;
    private final UserService userService;

    public CompareController(CompareService compareService, UserService userService) {
        this.compareService = compareService;
        this.userService = userService;
    }

    @PostMapping("/get-daily-data")
    public Response getDailyData(@Valid @RequestBody DailyDataRequest params, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        var date = params.date();
        User user = userService.findById(userId);

        double intake = compareService.calculateDailyIntake(userId, date);
        double expenditure = compareService.calculateDailyExpenditure(userId, date);
        Workout workout = compareService.getWorkoutByDate(userId, date);
        double dailyCalories = userService.calculateDailyCalories(user);
        double difference = intake - expenditure;

        Map<String, Object> result = new HashMap<>();
        result.put("intake", intake);
        result.put("expenditure", expenditure);
        result.put("workoutStatus", workout == null || workout.getStatus() == null
                ? null : workout.getStatus().name());
        result.put("workoutEstimatedCalories", workout == null ? null : workout.getEstimatedCalories());
        result.put("difference", difference);
        result.put("dailyCalories", dailyCalories);
        result.put("tipText", compareService.generateTipText(user, intake, expenditure, dailyCalories));
        result.put("selectedRecipes", compareService.getSelectedRecipes(userId, date));
        return Response.success(result);
    }

    @PostMapping("/add-recipe")
    public Response addRecipeToDailyIntake(@Valid @RequestBody AddIntakeRequest params, HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        String requestId = params.requestId() == null || params.requestId().isBlank()
                ? UUID.randomUUID().toString() : params.requestId();
        compareService.addRecipeToDailyIntake(userId, params.date(), normalizeRecipeId(params.recipeId()), requestId);
        return Response.success();
    }

    @PostMapping("/remove-recipe")
    @AuditedOperation(value = AuditEvent.DAILY_INTAKE_REMOVE,
            targetArgument = 0, targetProperty = "recipeId")
    public Response removeRecipeFromDailyIntake(@Valid @RequestBody RemoveRecipeRequest params,
                                                 HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        compareService.removeRecipeFromDailyIntake(
                userId, params.date(), normalizeRecipeId(params.recipeId()));
        return Response.success();
    }

    private String normalizeRecipeId(String recipeId) {
        return recipeId.startsWith("recipe_") ? recipeId.substring(7) : recipeId;
    }

    @PostMapping("/remove-intake")
    @AuditedOperation(value = AuditEvent.DAILY_INTAKE_REMOVE,
            targetArgument = 0, targetProperty = "intakeId")
    public Response removeDailyIntake(@Valid @RequestBody RemoveIntakeRequest params,
                                      HttpServletRequest request) {
        compareService.removeDailyIntake(
                AuthContext.getUserId(request), params.date(), params.intakeId());
        return Response.success();
    }
}
