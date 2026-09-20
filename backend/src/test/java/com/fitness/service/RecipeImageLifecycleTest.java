package com.fitness.service;

import com.fitness.entity.Recipe;
import com.fitness.entity.RecipeImage;
import com.fitness.mapper.IngredientMapper;
import com.fitness.mapper.RecipeImageMapper;
import com.fitness.mapper.RecipeMapper;
import com.fitness.service.impl.RecipeServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.*;

class RecipeImageLifecycleTest {
    private final RecipeMapper recipes = mock(RecipeMapper.class);
    private final RecipeImageMapper images = mock(RecipeImageMapper.class);
    private final IngredientMapper ingredients = mock(IngredientMapper.class);
    private final ImageService lifecycle = mock(ImageService.class);
    private final RecipeServiceImpl service = new RecipeServiceImpl();

    @BeforeEach void setup() {
        ReflectionTestUtils.setField(service, "recipeMapper", recipes);
        ReflectionTestUtils.setField(service, "recipeImageMapper", images);
        ReflectionTestUtils.setField(service, "ingredientMapper", ingredients);
        ReflectionTestUtils.setField(service, "imageService", lifecycle);
    }

    private Recipe recipe(String... urls) {
        Recipe recipe = new Recipe();
        recipe.setUserId(1L);
        recipe.setId(11L);
        recipe.setRecipeId("business-id");
        recipe.setImages(java.util.Arrays.stream(urls).map(url -> {
            RecipeImage image = new RecipeImage();
            image.setImageUrl(url);
            return image;
        }).toList());
        return recipe;
    }

    @Test void editRegistersOnlyRemovedUrlsAfterDatabaseChanges() {
        when(recipes.findByUserIdAndRecipeId(1L, "business-id")).thenReturn(recipe("old", "keep"));
        service.saveRecipe(recipe("keep", "new"));
        verify(lifecycle).validateImages(1L, List.of("keep", "new"), List.of("old", "keep"));
        var order = inOrder(images, lifecycle);
        order.verify(lifecycle).validateImages(anyLong(), anyCollection(), anyCollection());
        order.verify(images).deleteByRecipeDbId(11L);
        order.verify(images, times(2)).insert(any());
        order.verify(lifecycle).cleanupAfterCommit(List.of("old"));
    }

    @Test void failedDatabaseWriteDoesNotRegisterCleanup() {
        when(recipes.findByUserIdAndRecipeId(1L, "business-id")).thenReturn(recipe("old"));
        doThrow(new IllegalStateException("database unavailable")).when(recipes).update(any());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class, () -> service.saveRecipe(recipe("new")));
        verify(lifecycle, never()).cleanupAfterCommit(anyCollection());
    }

    @Test void recipeDeletionRegistersItsImagesAfterDeletingRows() {
        when(recipes.findByUserIdAndRecipeId(1L, "business-id")).thenReturn(recipe("old"));
        service.deleteRecipeByRecipeId(1L, "business-id");
        var order = inOrder(recipes, lifecycle);
        order.verify(recipes).findByUserIdAndRecipeId(1L, "business-id");
        order.verify(recipes).delete(11L);
        order.verify(lifecycle).cleanupAfterCommit(List.of("old"));
    }
}
