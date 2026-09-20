package com.fitness.controller;

import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.common.Response;
import com.fitness.dto.NamesRequest;
import com.fitness.entity.FoodDatabase;
import com.fitness.entity.ExerciseDatabase;
import com.fitness.service.DatabaseService;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

import java.util.List;

@RestController
@RequestMapping("/database")
public class DatabaseController {
    private final DatabaseService databaseService;

    public DatabaseController(DatabaseService databaseService) {
        this.databaseService = databaseService;
    }
    
    @GetMapping("/food/list")
    public Response getAllFoods() {
        List<FoodDatabase> foods = databaseService.getAllFoods();
        return Response.success(foods);
    }
    
    @GetMapping("/food/get/{foodName}")
    public Response getFoodByName(@PathVariable @Size(max = 50, message = "食材名称不能超过50个字符") String foodName) {
        FoodDatabase food = databaseService.getFoodByName(foodName);
        if (food == null) throw new BusinessException(ApiErrorCode.NOT_FOUND, "食材不存在");
        return Response.success(food);
    }
    
    @GetMapping("/exercise/list")
    public Response getAllExercises() {
        List<ExerciseDatabase> exercises = databaseService.getAllExercises();
        return Response.success(exercises);
    }
    
    @GetMapping("/exercise/get/{name}")
    public Response getExerciseByName(@PathVariable @Size(max = 50, message = "动作名称不能超过50个字符") String name) {
        ExerciseDatabase exercise = databaseService.getExerciseByName(name);
        if (exercise == null) throw new BusinessException(ApiErrorCode.NOT_FOUND, "训练动作不存在");
        return Response.success(exercise);
    }

    @GetMapping("/food/search")
    public Response searchFoods(@RequestParam(defaultValue = "") @Size(max = 50, message = "搜索词不能超过50个字符") String keyword,
                                @RequestParam(defaultValue = "") @Size(max = 30, message = "分类不能超过30个字符") String category,
                                @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不能为负数") int page,
                                @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少1条")
                                @Max(value = 50, message = "每页最多50条") int size) {
        return Response.success(databaseService.searchFoods(keyword, category, page, size));
    }

    @GetMapping("/exercise/search")
    public Response searchExercises(@RequestParam(defaultValue = "") @Size(max = 50, message = "搜索词不能超过50个字符") String keyword,
                                    @RequestParam(defaultValue = "") @Size(max = 30, message = "分类不能超过30个字符") String category,
                                    @RequestParam(defaultValue = "0") @Min(value = 0, message = "页码不能为负数") int page,
                                    @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少1条")
                                    @Max(value = 50, message = "每页最多50条") int size) {
        return Response.success(databaseService.searchExercises(keyword, category, page, size));
    }

    @GetMapping("/food/categories")
    public Response getFoodCategories() {
        return Response.success(databaseService.getFoodCategories());
    }

    @GetMapping("/exercise/categories")
    public Response getExerciseCategories() {
        return Response.success(databaseService.getExerciseCategories());
    }

    @PostMapping("/food/resolve")
    public Response resolveFoods(@Valid @RequestBody NamesRequest body) {
        return Response.success(databaseService.resolveFoods(body.names()));
    }

    @PostMapping("/exercise/resolve")
    public Response resolveExercises(@Valid @RequestBody NamesRequest body) {
        return Response.success(databaseService.resolveExercises(body.names()));
    }
}
