package com.fitness.controller;

import com.fitness.audit.AuditEvent;
import com.fitness.audit.AuditedOperation;
import com.fitness.auth.AuthContext;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.common.Response;
import com.fitness.common.PageResult;
import com.fitness.dto.RecipeCatalogItem;
import com.fitness.dto.RecipeSaveRequest;
import com.fitness.entity.Ingredient;
import com.fitness.entity.Recipe;
import com.fitness.entity.RecipeImage;
import com.fitness.service.RecipeService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/recipe")
public class RecipeController {
    private final RecipeService recipeService;

    public RecipeController(RecipeService recipeService) {
        this.recipeService = recipeService;
    }

    @GetMapping("/list")
    public Response getRecipes(HttpServletRequest request) {
        return Response.success(recipeService.getRecipesByUserId(AuthContext.getUserId(request)));
    }

    @GetMapping("/catalog")
    public Response getCatalog(@RequestParam(defaultValue = "") String keyword,
                               @RequestParam(defaultValue = "") String goal,
                               @RequestParam(defaultValue = "") String cuisine,
                               @RequestParam(defaultValue = "") String source,
                               @RequestParam(defaultValue = "0") int page,
                               @RequestParam(defaultValue = "20") int size,
                               HttpServletRequest request) {
        if (keyword.length() > 50 || (!goal.isBlank() && !goal.equals("减脂友好") && !goal.equals("增肌友好"))
                || (!cuisine.isBlank() && !cuisine.equals("中式家常"))
                || (!source.isBlank() && !source.equals("USER") && !source.equals("SYSTEM"))
                || page < 0 || size < 1 || size > 50) {
            throw new IllegalArgumentException("搜索参数无效");
        }
        return Response.success(recipeService.searchCatalog(
                AuthContext.getUserId(request), keyword, goal, cuisine, source, page, size));
    }

    @GetMapping("/get/{recipeId}")
    public Response getRecipe(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "菜谱编号格式无效") String recipeId,
            HttpServletRequest request) {
        Long userId = AuthContext.getUserId(request);
        RecipeCatalogItem recipe = recipeService.getCatalogItem(userId, recipeId);
        if (recipe == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "菜谱不存在");
        }
        return Response.success(recipe);
    }

    @PostMapping("/save")
    public Response saveRecipe(@Valid @RequestBody RecipeSaveRequest params, HttpServletRequest request) {
        Recipe recipe = new Recipe();
        recipe.setRecipeId(params.recipeId());
        recipe.setUserId(AuthContext.getUserId(request));
        recipe.setMealType(params.name().trim());
        recipe.setSteps(params.steps() == null ? "" : params.steps());
        recipe.setProtein(params.protein());
        recipe.setCarb(params.carb());
        recipe.setFat(params.fat());
        recipe.setCalorie(params.calorie());

        List<String> imageUrls = params.images() == null ? List.of() : params.images();
        List<RecipeImage> images = new ArrayList<>();
        for (int i = 0; i < imageUrls.size(); i++) {
            RecipeImage image = new RecipeImage();
            image.setImageUrl(imageUrls.get(i));
            image.setSortOrder(i);
            images.add(image);
        }
        recipe.setImages(images);

        recipe.setIngredients(params.ingredients().stream().map(item -> {
            Ingredient ingredient = new Ingredient();
            ingredient.setFoodName(item.foodName().trim());
            ingredient.setWeight(item.weight());
            ingredient.setProtein(item.protein() == null ? java.math.BigDecimal.ZERO : item.protein());
            ingredient.setCarb(item.carb() == null ? java.math.BigDecimal.ZERO : item.carb());
            ingredient.setFat(item.fat() == null ? java.math.BigDecimal.ZERO : item.fat());
            ingredient.setCalorie(item.calorie() == null ? java.math.BigDecimal.ZERO : item.calorie());
            return ingredient;
        }).toList());

        return Response.success(recipeService.saveRecipe(recipe));
    }

    @DeleteMapping("/delete/{recipeId}")
    @AuditedOperation(value = AuditEvent.RECIPE_DELETE, targetArgument = 0)
    public Response deleteRecipe(
            @PathVariable @Pattern(regexp = "[A-Za-z0-9_-]{1,50}", message = "菜谱编号格式无效") String recipeId,
            HttpServletRequest request) {
        if (recipeService.isSystemRecipe(recipeId)) {
            throw new BusinessException(ApiErrorCode.FORBIDDEN, "系统菜谱不可删除");
        }
        boolean deleted = recipeService.deleteRecipeByRecipeId(
                AuthContext.getUserId(request), recipeId);
        if (!deleted) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "菜谱不存在");
        }
        return Response.success();
    }
}
