package com.fitness.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public record RecipeSaveRequest(
        @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "菜谱编号格式无效") String recipeId,
        @NotBlank(message = "菜谱名称不能为空") @Size(max = 20, message = "菜谱名称不能超过20个字符") String name,
        @Size(max = 10000, message = "制作步骤不能超过10000个字符") String steps,
        @NotNull(message = "蛋白质不能为空") @DecimalMin(value = "0", message = "蛋白质不能为负数")
        @DecimalMax(value = "99999999.99", message = "蛋白质数值过大") BigDecimal protein,
        @NotNull(message = "碳水不能为空") @DecimalMin(value = "0", message = "碳水不能为负数")
        @DecimalMax(value = "99999999.99", message = "碳水数值过大") BigDecimal carb,
        @NotNull(message = "脂肪不能为空") @DecimalMin(value = "0", message = "脂肪不能为负数")
        @DecimalMax(value = "99999999.99", message = "脂肪数值过大") BigDecimal fat,
        @NotNull(message = "热量不能为空") @DecimalMin(value = "0", message = "热量不能为负数")
        @DecimalMax(value = "99999999.99", message = "热量数值过大") BigDecimal calorie,
        @Size(max = 9, message = "每个菜谱最多保存9张图片")
        List<@NotBlank(message = "图片地址不能为空") @Size(max = 255, message = "图片地址过长") String> images,
        @NotEmpty(message = "请至少添加一个食材") @Size(max = 100, message = "每个菜谱最多保存100种食材")
        List<@Valid IngredientRequest> ingredients) {

    public record IngredientRequest(
            @NotBlank(message = "食材名称不能为空") @Size(max = 50, message = "食材名称不能超过50个字符") String foodName,
            @NotNull(message = "食材重量不能为空") @DecimalMin(value = "0.01", message = "食材重量必须大于0")
            @DecimalMax(value = "99999999.99", message = "食材重量数值过大") BigDecimal weight,
            @DecimalMin(value = "0", message = "食材蛋白质不能为负数") BigDecimal protein,
            @DecimalMin(value = "0", message = "食材碳水不能为负数") BigDecimal carb,
            @DecimalMin(value = "0", message = "食材脂肪不能为负数") BigDecimal fat,
            @DecimalMin(value = "0", message = "食材热量不能为负数") BigDecimal calorie) {}
}
