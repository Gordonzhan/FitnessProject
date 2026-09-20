package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record RemoveRecipeRequest(
        @NotNull(message = "请选择日期") LocalDate date,
        @NotBlank(message = "请选择菜谱")
        @Pattern(regexp = "(?:recipe_)?[A-Za-z0-9_-]{1,50}", message = "菜谱编号格式无效") String recipeId) {}
