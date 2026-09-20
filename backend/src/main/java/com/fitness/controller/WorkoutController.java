package com.fitness.controller;

import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.common.Response;
import com.fitness.dto.WorkoutSaveRequest;
import com.fitness.dto.WorkoutCompleteRequest;
import com.fitness.entity.Exercise;
import com.fitness.entity.Workout;
import com.fitness.service.WorkoutService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/workout")
public class WorkoutController {
    private final WorkoutService workoutService;

    public WorkoutController(WorkoutService workoutService) {
        this.workoutService = workoutService;
    }

    @GetMapping("/list")
    public Response getWorkouts(HttpServletRequest request) {
        return Response.success(workoutService.getWorkoutsByUserId(AuthContext.getUserId(request)));
    }

    @GetMapping("/get/{date}")
    public Response getWorkoutByDate(
            @PathVariable @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}", message = "日期格式应为YYYY-MM-DD") String date,
            HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        return Response.success(workoutService.getWorkoutByDate(userId, LocalDate.parse(date)));
    }

    @PostMapping("/save")
    public Response saveWorkout(@Valid @RequestBody WorkoutSaveRequest params, HttpServletRequest request) {
        Workout workout = new Workout();
        workout.setWorkoutId(params.workoutId());
        workout.setDate(params.date());
        workout.setStatus(params.status());
        workout.setTotalCalories(params.totalCalories());
        workout.setUserId(AuthContext.getUserId(request));
        workout.setExercises(params.exercises().stream().map(item -> {
            Exercise exercise = new Exercise();
            exercise.setName(item.name().trim());
            exercise.setWeight(item.weight());
            exercise.setSets(item.sets());
            exercise.setReps(item.reps());
            exercise.setCalories(item.calories());
            return exercise;
        }).toList());
        return Response.success(workoutService.saveWorkout(workout));
    }

    @PostMapping("/complete/{workoutId}")
    @AuditedOperation(value = AuditEvent.WORKOUT_COMPLETE, targetArgument = 0)
    public Response completeWorkout(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "训练编号格式无效") String workoutId,
            @Valid @RequestBody WorkoutCompleteRequest params,
            HttpServletRequest request) {
        return Response.success(workoutService.completeWorkout(
                AuthContext.getUserId(request), workoutId, params.actualCalories()));
    }

    @PostMapping("/cancel/{workoutId}")
    @AuditedOperation(value = AuditEvent.WORKOUT_PLAN_CANCEL, targetArgument = 0)
    public Response cancelWorkout(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "训练编号格式无效") String workoutId,
            HttpServletRequest request) {
        return Response.success(workoutService.cancelWorkout(
                AuthContext.getUserId(request), workoutId));
    }

    @DeleteMapping("/delete/{workoutId}")
    @AuditedOperation(value = AuditEvent.WORKOUT_DELETE, targetArgument = 0)
    public Response deleteWorkout(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "训练编号格式无效") String workoutId,
            HttpServletRequest request) {
        boolean deleted = workoutService.deleteWorkoutByWorkoutId(
                AuthContext.getUserId(request), workoutId);
        if (!deleted) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "训练记录不存在");
        }
        return Response.success();
    }
}
