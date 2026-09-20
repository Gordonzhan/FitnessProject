package com.fitness.service;

import com.fitness.common.PageResult;
import com.fitness.dto.RecipeCatalogItem;
import com.fitness.entity.Recipe;

import java.util.List;

public interface RecipeService {
    /** 查询用户创建的全部菜谱，并装配图片与食材明细。 */
    List<Recipe> getRecipesByUserId(Long userId);
    /** 根据业务编号查询属于指定用户的菜谱。 */
    Recipe getRecipeByRecipeId(Long userId, String recipeId);
    /** 从用户菜谱和系统菜谱的统一目录中查询单个菜谱。 */
    RecipeCatalogItem getCatalogItem(Long userId, String recipeId);
    /** 分页搜索统一菜谱目录，用户菜谱优先于系统菜谱返回。 */
    PageResult<RecipeCatalogItem> searchCatalog(Long userId, String keyword, String goal, String cuisine,
                                                String source, int page, int size);
    /** 判断业务编号是否属于不可直接编辑、删除的系统菜谱。 */
    boolean isSystemRecipe(String recipeId);
    /** 新建或更新用户菜谱，并整体同步图片和食材明细。 */
    Recipe saveRecipe(Recipe recipe);
    /** 删除属于指定用户的菜谱及其关联数据。 */
    boolean deleteRecipeByRecipeId(Long userId, String recipeId);
}
