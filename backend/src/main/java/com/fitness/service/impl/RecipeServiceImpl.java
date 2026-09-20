package com.fitness.service.impl;

import com.fitness.common.PageResult;
import com.fitness.dto.RecipeCatalogItem;
import com.fitness.entity.Recipe;
import com.fitness.entity.RecipeImage;
import com.fitness.entity.Ingredient;
import com.fitness.entity.SystemRecipeIngredient;
import com.fitness.entity.SystemRecipeTemplate;
import com.fitness.mapper.RecipeMapper;
import com.fitness.mapper.RecipeImageMapper;
import com.fitness.mapper.IngredientMapper;
import com.fitness.mapper.SystemRecipeIngredientMapper;
import com.fitness.mapper.SystemRecipeTemplateMapper;
import com.fitness.service.RecipeService;
import com.fitness.service.ImageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class RecipeServiceImpl implements RecipeService {
    private static final Logger logger = LoggerFactory.getLogger(RecipeServiceImpl.class);
    
    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private RecipeImageMapper recipeImageMapper;
    @Autowired
    private IngredientMapper ingredientMapper;
    @Autowired
    private ImageService imageService;
    @Autowired
    private SystemRecipeTemplateMapper systemRecipeTemplateMapper;
    @Autowired
    private SystemRecipeIngredientMapper systemRecipeIngredientMapper;
    
    @Override
    public List<Recipe> getRecipesByUserId(Long userId) {
        List<Recipe> recipes = recipeMapper.findByUserId(userId);
        if (recipes.isEmpty()) return recipes;

        List<Long> ids = recipes.stream().map(Recipe::getId).toList();
        Map<Long, List<RecipeImage>> imagesByRecipe = recipeImageMapper.findByRecipeDbIds(ids).stream()
                .collect(Collectors.groupingBy(RecipeImage::getRecipeDbId));
        Map<Long, List<Ingredient>> ingredientsByRecipe = ingredientMapper.findByRecipeDbIds(ids).stream()
                .collect(Collectors.groupingBy(Ingredient::getRecipeDbId));
        recipes.forEach(recipe -> {
            recipe.setImages(imagesByRecipe.getOrDefault(recipe.getId(), List.of()));
            recipe.setIngredients(ingredientsByRecipe.getOrDefault(recipe.getId(), List.of()));
        });
        return recipes;
    }
    
    @Override
    public Recipe getRecipeByRecipeId(Long userId, String recipeId) {
        return recipeMapper.findByUserIdAndRecipeId(userId, recipeId);
    }

    @Override
    public RecipeCatalogItem getCatalogItem(Long userId, String recipeId) {
        Recipe userRecipe = recipeMapper.findByUserIdAndRecipeId(userId, recipeId);
        if (userRecipe != null) return fromUserRecipe(userRecipe);
        SystemRecipeTemplate template = systemRecipeTemplateMapper.findByRecipeId(recipeId);
        if (template == null) return null;
        template.setIngredients(systemRecipeIngredientMapper.findByTemplateDbId(template.getId()));
        return fromSystemRecipe(template);
    }

    @Override
    public PageResult<RecipeCatalogItem> searchCatalog(Long userId, String rawKeyword, String rawGoal,
                                                       String rawCuisine, String rawSource, int page, int size) {
        String keyword = rawKeyword == null ? "" : rawKeyword.trim();
        String goal = rawGoal == null ? "" : rawGoal.trim();
        String cuisine = rawCuisine == null ? "" : rawCuisine.trim();
        String source = rawSource == null ? "" : rawSource.trim();
        int safePage = Math.max(page, 0);
        int safeSize = Math.max(1, Math.min(size, 50));
        // 用户菜谱没有目标标签，仅在“全部”中显示；分类页只展示经过标注的系统模板。
        boolean includeUsers = !"SYSTEM".equals(source)
                && ("USER".equals(source) || (goal.isEmpty() && cuisine.isEmpty()));
        boolean includeSystems = !"USER".equals(source);
        long userCount = includeUsers
                ? recipeMapper.countByUserIdAndKeyword(userId, keyword) : 0;
        long systemCount = includeSystems ? systemRecipeTemplateMapper.countEnabled(keyword, goal, cuisine) : 0;
        long offset = (long) safePage * safeSize;
        List<RecipeCatalogItem> items = new ArrayList<>(safeSize);

        if (offset < userCount) {
            List<Recipe> users = recipeMapper.searchByUserId(userId, keyword, (int) offset, safeSize);
            hydrateUsers(users);
            users.stream().map(this::fromUserRecipe).forEach(items::add);
        }
        int remaining = safeSize - items.size();
        if (remaining > 0 && includeSystems) {
            int systemOffset = (int) Math.max(0, offset - userCount);
            List<SystemRecipeTemplate> systems = systemRecipeTemplateMapper.searchEnabled(
                    keyword, goal, cuisine, systemOffset, remaining);
            hydrateSystems(systems);
            systems.stream().map(this::fromSystemRecipe).forEach(items::add);
        }
        return new PageResult<>(items, userCount + systemCount, safePage, safeSize);
    }

    @Override
    public boolean isSystemRecipe(String recipeId) {
        return recipeId != null && systemRecipeTemplateMapper.findByRecipeId(recipeId) != null;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public Recipe saveRecipe(Recipe recipe) {
        // recipeId 是前端业务标识，id 是数据库自增主键。编辑时始终以
        // recipeId 查询真实主键，避免客户端把二者混用后破坏关联数据。
        if (recipe.getRecipeId() == null || recipe.getRecipeId().isBlank()) {
            recipe.setRecipeId(UUID.randomUUID().toString());
        }
        if (recipe.getRecipeId().startsWith("sys_")) {
            throw new IllegalArgumentException("系统菜谱不可直接修改，请先复制为我的菜谱");
        }

        Recipe existingRecipe = recipeMapper.findByUserIdAndRecipeId(
                recipe.getUserId(), recipe.getRecipeId());
        List<String> previousUrls = imageUrls(existingRecipe);
        List<String> currentUrls = imageUrls(recipe);
        imageService.validateImages(recipe.getUserId(), currentUrls, previousUrls);
        // 唯一键 + 原子 upsert 让同一业务 ID 的首次并发重试落到同一父记录。
        if (existingRecipe != null) {
            recipe.setId(existingRecipe.getId());
            recipeMapper.update(recipe);
        } else {
            recipe.setId(null);
            recipeMapper.insert(recipe);
            // 不依赖不同 JDBC 驱动对 ON DUPLICATE KEY 的 generatedKeys 差异。
            Recipe persisted = recipeMapper.findByUserIdAndRecipeId(recipe.getUserId(), recipe.getRecipeId());
            if (persisted == null) throw new IllegalStateException("菜谱保存后无法读取");
            recipe.setId(persisted.getId());
        }

        // 新增时删除为空；编辑或幂等重试时整体替换，避免重复追加图片和食材。
        recipeImageMapper.deleteByRecipeDbId(recipe.getId());
        ingredientMapper.deleteByRecipeDbId(recipe.getId());
        
        logger.info("Recipe saved with id: {}", recipe.getId());
        
        // 处理关联的图片
        if (recipe.getImages() != null) {
            for (RecipeImage image : recipe.getImages()) {
                image.setRecipeDbId(recipe.getId());
                recipeImageMapper.insert(image);
            }
        }
        
        // 处理关联的食材
        if (recipe.getIngredients() != null) {
            for (Ingredient ingredient : recipe.getIngredients()) {
                ingredient.setRecipeDbId(recipe.getId());
                ingredientMapper.insert(ingredient);
            }
        }
        
        imageService.cleanupAfterCommit(previousUrls.stream().filter(url -> !currentUrls.contains(url)).toList());
        return recipe;
    }
    
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteRecipeByRecipeId(Long userId, String recipeId) {
        Recipe recipe = recipeMapper.findByUserIdAndRecipeId(userId, recipeId);
        if (recipe == null) {
            return false;
        }

        Long recipeDbId = recipe.getId();
        logger.info("Deleting recipe with business id: {}, database id: {}", recipeId, recipeDbId);
        
        // 删除关联的图片
        recipeImageMapper.deleteByRecipeDbId(recipeDbId);
        logger.info("Deleted associated images for recipe database id: {}", recipeDbId);
        
        // 删除关联的食材
        ingredientMapper.deleteByRecipeDbId(recipeDbId);
        logger.info("Deleted associated ingredients for recipe database id: {}", recipeDbId);
        
        // 删除菜谱
        recipeMapper.delete(recipeDbId);
        imageService.cleanupAfterCommit(imageUrls(recipe));
        logger.info("Deleted recipe with database id: {}", recipeDbId);
        return true;
    }

    /** 提取菜谱中的非空图片地址，供所有权校验和 OSS 清理使用。 */
    private List<String> imageUrls(Recipe recipe) {
        return recipe == null || recipe.getImages() == null ? List.of()
                : recipe.getImages().stream().map(RecipeImage::getImageUrl).toList();
    }

    /** 批量装配用户菜谱的图片和食材，避免逐条查询造成 N+1 问题。 */
    private void hydrateUsers(List<Recipe> recipes) {
        if (recipes.isEmpty()) return;
        List<Long> ids = recipes.stream().map(Recipe::getId).toList();
        Map<Long, List<RecipeImage>> images = recipeImageMapper.findByRecipeDbIds(ids).stream()
                .collect(Collectors.groupingBy(RecipeImage::getRecipeDbId));
        Map<Long, List<Ingredient>> ingredients = ingredientMapper.findByRecipeDbIds(ids).stream()
                .collect(Collectors.groupingBy(Ingredient::getRecipeDbId));
        recipes.forEach(r -> {
            r.setImages(images.getOrDefault(r.getId(), List.of()));
            r.setIngredients(ingredients.getOrDefault(r.getId(), List.of()));
        });
    }

    /** 批量装配系统菜谱的食材快照，避免逐条查询造成 N+1 问题。 */
    private void hydrateSystems(List<SystemRecipeTemplate> templates) {
        if (templates.isEmpty()) return;
        Map<Long, List<SystemRecipeIngredient>> grouped = systemRecipeIngredientMapper
                .findByTemplateDbIds(templates.stream().map(SystemRecipeTemplate::getId).toList()).stream()
                .collect(Collectors.groupingBy(SystemRecipeIngredient::getTemplateDbId));
        templates.forEach(t -> t.setIngredients(grouped.getOrDefault(t.getId(), List.of())));
    }

    /** 将用户菜谱转换为统一目录展示对象，并标记为可编辑、可删除。 */
    private RecipeCatalogItem fromUserRecipe(Recipe recipe) {
        RecipeCatalogItem item = new RecipeCatalogItem();
        item.setRecipeId(recipe.getRecipeId()); item.setMealType(recipe.getMealType());
        item.setSteps(recipe.getSteps()); item.setProtein(recipe.getProtein()); item.setCarb(recipe.getCarb());
        item.setFat(recipe.getFat()); item.setCalorie(recipe.getCalorie()); item.setImages(recipe.getImages());
        item.setIngredients(recipe.getIngredients()); item.setSourceType("USER");
        item.setEditable(true); item.setDeletable(true); item.setCategory("我的菜谱");
        return item;
    }

    /** 将系统菜谱模板转换为统一目录展示对象，并标记为只读。 */
    private RecipeCatalogItem fromSystemRecipe(SystemRecipeTemplate template) {
        RecipeCatalogItem item = new RecipeCatalogItem();
        item.setRecipeId(template.getRecipeId()); item.setMealType(template.getMealType());
        item.setSteps(template.getSteps()); item.setProtein(template.getProtein()); item.setCarb(template.getCarb());
        item.setFat(template.getFat()); item.setCalorie(template.getCalorie());
        if (template.getCoverImageUrl() == null || template.getCoverImageUrl().isBlank()) {
            item.setImages(List.of());
        } else {
            RecipeImage cover = new RecipeImage();
            cover.setImageUrl(template.getCoverImageUrl());
            cover.setSortOrder(0);
            item.setImages(List.of(cover));
        }
        List<Ingredient> ingredients = template.getIngredients().stream().map(source -> {
            Ingredient target = new Ingredient();
            target.setFoodName(source.getFoodName()); target.setWeight(source.getWeight());
            target.setProtein(source.getProtein()); target.setCarb(source.getCarb());
            target.setFat(source.getFat()); target.setCalorie(source.getCalorie());
            return target;
        }).toList();
        item.setIngredients(ingredients); item.setSourceType("SYSTEM");
        item.setEditable(false); item.setDeletable(false); item.setCategory(template.getCategory());
        item.setCuisineType(template.getCuisineType());
        item.setGoalTags(template.getGoalTags()); item.setServingDescription(template.getServingDescription());
        item.setAllergenInfo(template.getAllergenInfo()); item.setSourceName(template.getSourceName());
        item.setSourceRef(template.getSourceRef()); item.setDataVersion(template.getDataVersion());
        return item;
    }
}
