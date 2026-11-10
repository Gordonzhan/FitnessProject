package com.fitness.service;

import com.fitness.ai.AgentClient;
import com.fitness.dto.FoodRecognitionDraftResponse;
import com.fitness.dto.FoodRecognitionRequest;
import com.fitness.dto.FoodRecognitionResponse;
import com.fitness.entity.FoodDatabase;
import com.fitness.repository.FoodDatabaseRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** 把视觉模型结果匹配到业务食材库，并用数据库每 100 克数据生成可编辑营养草稿。 */
@Service
public class FoodRecognitionDraftService {
    private static final Logger log = LoggerFactory.getLogger(FoodRecognitionDraftService.class);
    private static final int MAX_CANDIDATES = 3;
    private static final String GENERIC_SOURCE = "SYSTEM_GENERIC_ESTIMATE";
    private final AgentClient agentClient;
    private final FoodDatabaseRepository foodRepository;
    private final ImageService imageService;
    private final FoodCandidateObservationService candidateObservationService;

    public FoodRecognitionDraftService(AgentClient agentClient, FoodDatabaseRepository foodRepository,
                                       ImageService imageService,
                                       FoodCandidateObservationService candidateObservationService) {
        this.agentClient = agentClient;
        this.foodRepository = foodRepository;
        this.imageService = imageService;
        this.candidateObservationService = candidateObservationService;
    }

    /** 图片必须属于当前用户；任意公网 URL 不允许进入正式产品识别链路。 */
    public FoodRecognitionDraftResponse createDraft(Long userId, FoodRecognitionRequest request, String requestId) {
        imageService.validateAiRecognitionImages(userId, request.imageUrls());
        FoodRecognitionResponse agentResult = agentClient.recognizeFood(request, requestId);
        List<FoodDatabase> foods = foodRepository.findByEnabledTrueOrderBySortOrderDescFoodNameAsc();
        List<FoodRecognitionDraftResponse.DraftItem> items = agentResult.foods().stream()
                .map(item -> toDraftItem(item, foods)).toList();
        List<String> warnings = new ArrayList<>(agentResult.warnings());
        if (items.stream().anyMatch(item -> item.matchedFood() == null)) {
            warnings.add("部分识别项需要选择基础食材后才能保存");
        }
        observeUnmatched(items);
        return new FoodRecognitionDraftResponse(agentResult.requestId(), agentResult.status(),
                agentResult.provider(), items, agentResult.overallConfidence(), true, List.copyOf(warnings),
                recipeSuggestion(agentResult.recipeSuggestion()),
                agentResult.recipeGenerationStatus() == null ? "SKIPPED" : agentResult.recipeGenerationStatus());
    }

    private FoodRecognitionDraftResponse.RecipeSuggestion recipeSuggestion(
            FoodRecognitionResponse.RecipeSuggestion suggestion) {
        if (suggestion == null) return null;
        return new FoodRecognitionDraftResponse.RecipeSuggestion(
                suggestion.name(), suggestion.steps(), suggestion.confidence(),
                suggestion.provider(), suggestion.disclaimer());
    }

    private FoodRecognitionDraftResponse.DraftItem toDraftItem(
            FoodRecognitionResponse.FoodEstimate estimate, List<FoodDatabase> foods) {
        MatchResult match = match(estimate.name(), foods);
        BigDecimal minimum = estimate.weightRangeGrams() != null && !estimate.weightRangeGrams().isEmpty()
                ? estimate.weightRangeGrams().get(0) : estimate.estimatedWeightGrams();
        BigDecimal maximum = estimate.weightRangeGrams() != null && estimate.weightRangeGrams().size() > 1
                ? estimate.weightRangeGrams().get(1) : estimate.estimatedWeightGrams();
        return new FoodRecognitionDraftResponse.DraftItem(
                estimate.name(), estimate.estimatedWeightGrams(), minimum, maximum,
                estimate.cookingMethod(), estimate.confidence(), match.status(), option(match.matched()),
                match.candidates().stream().map(this::option).toList(),
                match.matched() == null ? zeroNutrition() : nutrition(match.matched(), estimate.estimatedWeightGrams()));
    }

    /** 候选统计属于改进数据，不得因统计表异常中断用户的识别主流程。 */
    private void observeUnmatched(List<FoodRecognitionDraftResponse.DraftItem> items) {
        List<FoodCandidateObservationService.Observation> observations = items.stream()
                .filter(item -> item.matchedFood() == null)
                .map(item -> new FoodCandidateObservationService.Observation(
                        item.recognizedName(), item.cookingMethod()))
                .toList();
        if (observations.isEmpty()) return;
        try {
            candidateObservationService.observe(observations);
        } catch (RuntimeException error) {
            log.warn("AI_FOOD_CANDIDATE_OBSERVE_FAILED count={} reason={}",
                    observations.size(), error.getClass().getSimpleName());
        }
    }

    private MatchResult match(String recognizedName, List<FoodDatabase> foods) {
        String target = normalize(recognizedName);
        List<FoodDatabase> standardMatches = foods.stream()
                .filter(food -> !isGenericFallback(food))
                .filter(food -> normalize(food.getFoodName()).equals(target)).toList();
        if (standardMatches.size() == 1) {
            return new MatchResult("EXACT_NAME", standardMatches.get(0), standardMatches);
        }

        List<FoodDatabase> aliasMatches = foods.stream()
                .filter(food -> !isGenericFallback(food))
                .filter(food -> aliases(food).stream()
                .anyMatch(alias -> normalize(alias).equals(target))).toList();
        if (aliasMatches.size() == 1) {
            return new MatchResult("EXACT_ALIAS", aliasMatches.get(0), aliasMatches);
        }

        // 模糊结果只能作为候选，不能自动落库；用户需要明确选择生/熟状态等歧义。
        List<FoodDatabase> scoredCandidates = foods.stream()
                .filter(food -> !isGenericFallback(food))
                .map(food -> new ScoredFood(food, similarity(target, food)))
                .filter(item -> item.score() > 0)
                .sorted(Comparator.comparingInt(ScoredFood::score).reversed()
                        .thenComparing(item -> item.food().getFoodName()))
                .limit(MAX_CANDIDATES).map(ScoredFood::food).toList();
        List<FoodDatabase> candidates = scoredCandidates;
        if (!aliasMatches.isEmpty()) {
            candidates = aliasMatches.stream().limit(MAX_CANDIDATES).toList();
        }
        // 配方或品牌波动大的通用估算即使名称完全命中，也只能作为待确认候选。
        List<FoodDatabase> genericMatches = foods.stream()
                .filter(this::isGenericFallback)
                .filter(food -> normalize(food.getFoodName()).equals(target)
                        || aliases(food).stream().anyMatch(alias -> normalize(alias).equals(target)))
                .toList();
        if (candidates.isEmpty() && !genericMatches.isEmpty()) {
            candidates = genericMatches.stream().limit(MAX_CANDIDATES).toList();
        }
        // 仅在没有更具体的匹配结果时提供通用兜底，且仍须由用户明确点击确认。
        if (candidates.isEmpty()) {
            candidates = genericFallbacks(target, foods).stream().limit(MAX_CANDIDATES).toList();
        }
        return new MatchResult(candidates.isEmpty() ? "UNMATCHED" : "NEEDS_SELECTION", null, candidates);
    }

    private List<FoodDatabase> genericFallbacks(String target, List<FoodDatabase> foods) {
        List<String> names = new ArrayList<>();
        if (target.contains("鱼")) names.add("鱼肉（熟，通用）");
        if (target.contains("鸡")) names.add("鸡肉（熟，通用）");
        if (target.contains("菜") || target.contains("蔬菜") || target.contains("青菜")) {
            names.add("绿叶蔬菜（熟，通用）");
            names.add("混合蔬菜（熟，通用）");
        }
        return foods.stream().filter(food -> names.contains(food.getFoodName())).toList();
    }

    private int similarity(String target, FoodDatabase food) {
        if (target.isEmpty()) return 0;
        int best = scoreText(target, normalize(food.getFoodName()));
        for (String alias : aliases(food)) best = Math.max(best, scoreText(target, normalize(alias)));
        return best;
    }

    private int scoreText(String left, String right) {
        if (left.equals(right)) return 100;
        if (left.contains(right) || right.contains(left)) return 80 - Math.abs(left.length() - right.length());
        int distance = levenshtein(left, right);
        int longest = Math.max(left.length(), right.length());
        return longest > 0 && distance <= Math.max(1, longest / 3) ? 50 - distance : 0;
    }

    private int levenshtein(String left, String right) {
        int[] previous = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) previous[j] = j;
        for (int i = 1; i <= left.length(); i++) {
            int[] current = new int[right.length() + 1];
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int replace = previous[j - 1] + (left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), replace);
            }
            previous = current;
        }
        return previous[right.length()];
    }

    private List<String> aliases(FoodDatabase food) {
        if (food.getAliases() == null || food.getAliases().isBlank()) return List.of();
        return List.of(food.getAliases().split("[,，;；]"));
    }

    private String normalize(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\s\\p{Punct}，。；、（）()]+", "");
    }

    private FoodRecognitionDraftResponse.FoodOption option(FoodDatabase food) {
        if (food == null) return null;
        return new FoodRecognitionDraftResponse.FoodOption(food.getId(), food.getFoodName(), food.getCategory(),
                food.getServingState(), food.getProtein(), food.getCarb(), food.getFat(), food.getCalorie(),
                isGenericFallback(food));
    }

    private boolean isGenericFallback(FoodDatabase food) {
        return GENERIC_SOURCE.equals(food.getSourceName());
    }

    private FoodRecognitionDraftResponse.Nutrition nutrition(FoodDatabase food, BigDecimal grams) {
        BigDecimal ratio = grams.divide(new BigDecimal("100"), 6, RoundingMode.HALF_UP);
        return new FoodRecognitionDraftResponse.Nutrition(scale(food.getCalorie().multiply(ratio)),
                scale(food.getProtein().multiply(ratio)), scale(food.getCarb().multiply(ratio)),
                scale(food.getFat().multiply(ratio)));
    }

    private FoodRecognitionDraftResponse.Nutrition zeroNutrition() {
        return new FoodRecognitionDraftResponse.Nutrition(BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO);
    }

    private BigDecimal scale(BigDecimal value) { return value.setScale(1, RoundingMode.HALF_UP); }

    private record MatchResult(String status, FoodDatabase matched, List<FoodDatabase> candidates) {}
    private record ScoredFood(FoodDatabase food, int score) {}
}
