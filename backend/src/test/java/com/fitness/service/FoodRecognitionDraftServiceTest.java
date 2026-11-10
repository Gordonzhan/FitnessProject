package com.fitness.service;

import com.fitness.ai.AgentClient;
import com.fitness.dto.FoodRecognitionRequest;
import com.fitness.dto.FoodRecognitionResponse;
import com.fitness.entity.FoodDatabase;
import com.fitness.repository.FoodDatabaseRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FoodRecognitionDraftServiceTest {
    private final AgentClient agent = mock(AgentClient.class);
    private final FoodDatabaseRepository foods = mock(FoodDatabaseRepository.class);
    private final ImageService images = mock(ImageService.class);
    private final FoodCandidateObservationService observations = mock(FoodCandidateObservationService.class);
    private final FoodRecognitionDraftService service =
            new FoodRecognitionDraftService(agent, foods, images, observations);

    @Test
    void uniqueAliasIsMatchedAndNutritionComesFromDatabase() {
        FoodDatabase rice = food("米饭", "白米饭,熟米饭", "熟重", "2.6", "25.9", "0.3", "116");
        when(foods.findByEnabledTrueOrderBySortOrderDescFoodNameAsc()).thenReturn(List.of(rice));
        when(agent.recognizeFood(any(), eq("req-1"))).thenReturn(agentResult("熟米饭", "180"));
        FoodRecognitionRequest request = request();

        var draft = service.createDraft(7L, request, "req-1");

        verify(images).validateAiRecognitionImages(7L, request.imageUrls());
        assertEquals("EXACT_ALIAS", draft.items().get(0).matchStatus());
        assertEquals("米饭", draft.items().get(0).matchedFood().foodName());
        assertEquals(new BigDecimal("208.8"), draft.items().get(0).nutrition().caloriesKcal());
        assertEquals("AI 米饭", draft.recipeSuggestion().name());
        assertEquals("SUCCEEDED", draft.recipeGenerationStatus());
    }

    @Test
    void ambiguousAliasRequiresExplicitUserSelection() {
        FoodDatabase cooked = food("熟鸡胸肉", "鸡胸", "熟重", "32.1", "0", "3.2", "166");
        FoodDatabase raw = food("生鸡胸肉", "鸡胸", "生重", "22.5", "0", "1.9", "112");
        when(foods.findByEnabledTrueOrderBySortOrderDescFoodNameAsc()).thenReturn(List.of(cooked, raw));
        when(agent.recognizeFood(any(), eq("req-1"))).thenReturn(agentResult("鸡胸", "150"));

        var item = service.createDraft(7L, request(), "req-1").items().get(0);

        assertEquals("NEEDS_SELECTION", item.matchStatus());
        assertNull(item.matchedFood());
        assertEquals(2, item.candidates().size());
        assertEquals(BigDecimal.ZERO, item.nutrition().caloriesKcal());
    }

    @Test
    void cookedDishGetsGenericFallbackButStillRequiresUserSelection() {
        FoodDatabase genericFish = food("鱼肉（熟，通用）", "熟鱼肉", "熟重",
                "22", "0", "6", "145");
        genericFish.setSourceName("SYSTEM_GENERIC_ESTIMATE");
        when(foods.findByEnabledTrueOrderBySortOrderDescFoodNameAsc())
                .thenReturn(List.of(genericFish));
        when(agent.recognizeFood(any(), eq("req-1"))).thenReturn(agentResult("清蒸鱼", "150"));

        var item = service.createDraft(7L, request(), "req-1").items().get(0);

        assertEquals("NEEDS_SELECTION", item.matchStatus());
        assertNull(item.matchedFood());
        assertEquals("鱼肉（熟，通用）", item.candidates().get(0).foodName());
        assertEquals(true, item.candidates().get(0).genericFallback());
        verify(observations).observe(any());
    }

    @Test
    void genericAliasIsNeverAutoMatched() {
        FoodDatabase genericFish = food("鱼肉（熟，通用）", "熟鱼肉,普通鱼肉", "熟重",
                "22", "0", "6", "145");
        genericFish.setSourceName("SYSTEM_GENERIC_ESTIMATE");
        when(foods.findByEnabledTrueOrderBySortOrderDescFoodNameAsc())
                .thenReturn(List.of(genericFish));
        when(agent.recognizeFood(any(), eq("req-1"))).thenReturn(agentResult("熟鱼肉", "150"));

        var item = service.createDraft(7L, request(), "req-1").items().get(0);

        assertEquals("NEEDS_SELECTION", item.matchStatus());
        assertNull(item.matchedFood());
        assertEquals(true, item.candidates().get(0).genericFallback());
    }

    @Test
    void variableDrinkExactAliasStillRequiresConfirmation() {
        FoodDatabase cola = food("含糖可乐（通用估算）", "可乐,普通可乐", "液体",
                "0", "10.6", "0", "42");
        cola.setSourceName("SYSTEM_GENERIC_ESTIMATE");
        when(foods.findByEnabledTrueOrderBySortOrderDescFoodNameAsc()).thenReturn(List.of(cola));
        when(agent.recognizeFood(any(), eq("req-1"))).thenReturn(agentResult("可乐", "330"));

        var item = service.createDraft(7L, request(), "req-1").items().get(0);

        assertEquals("NEEDS_SELECTION", item.matchStatus());
        assertNull(item.matchedFood());
        assertEquals("含糖可乐（通用估算）", item.candidates().get(0).foodName());
        assertEquals(true, item.candidates().get(0).genericFallback());
    }

    private FoodRecognitionRequest request() {
        return new FoodRecognitionRequest(List.of("https://bucket.example/users/7/food.jpg"),
                new FoodRecognitionRequest.UserContext(null, null, null, List.of()), null);
    }

    private FoodRecognitionResponse agentResult(String name, String weight) {
        BigDecimal grams = new BigDecimal(weight);
        return new FoodRecognitionResponse("req-1", "SUCCEEDED", "fake", List.of(
                new FoodRecognitionResponse.FoodEstimate(name, grams,
                        List.of(grams.subtract(BigDecimal.TEN), grams.add(BigDecimal.TEN)),
                        "蒸制", new FoodRecognitionResponse.NutritionEstimate(
                        BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE),
                        new BigDecimal("0.85"))), new BigDecimal("0.85"), true, List.of(),
                new FoodRecognitionResponse.RecipeSuggestion(
                        "AI 米饭", List.of("加热后装盘。"), new BigDecimal("0.70"),
                        "fake", "AI 推测，请按实际情况修改"),
                "SUCCEEDED");
    }

    private FoodDatabase food(String name, String aliases, String state,
                              String protein, String carb, String fat, String calorie) {
        FoodDatabase food = new FoodDatabase();
        food.setId((long) Math.abs(name.hashCode()));
        food.setFoodName(name);
        food.setAliases(aliases);
        food.setCategory("测试");
        food.setServingState(state);
        food.setProtein(new BigDecimal(protein));
        food.setCarb(new BigDecimal(carb));
        food.setFat(new BigDecimal(fat));
        food.setCalorie(new BigDecimal(calorie));
        return food;
    }
}
