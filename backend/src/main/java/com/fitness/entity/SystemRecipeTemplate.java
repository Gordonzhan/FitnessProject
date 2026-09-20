package com.fitness.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SystemRecipeTemplate {
    /** 系统菜谱模板的数据库主键。 */
    @JsonIgnore
    private Long id;
    /** 对外公开且稳定的系统菜谱业务编号。 */
    private String recipeId;
    /** 系统菜谱名称（历史字段名为 meal_type）。 */
    private String mealType;
    /** 用于模糊搜索的名称或食材别名。 */
    private String aliases;
    /** 菜谱业务分类。 */
    private String category;
    /** 菜系分类，如中式家常或西式简餐。 */
    private String cuisineType;
    /** 系统菜谱封面的 OSS 访问地址。 */
    private String coverImageUrl;
    /** 适用健身目标标签，如减脂友好或增肌友好。 */
    private String goalTags;
    /** 每份菜谱的份量说明。 */
    private String servingDescription;
    /** 菜谱制作步骤。 */
    private String steps;
    /** 每份蛋白质含量，单位：克。 */
    private BigDecimal protein;
    /** 每份碳水化合物含量，单位：克。 */
    private BigDecimal carb;
    /** 每份脂肪含量，单位：克。 */
    private BigDecimal fat;
    /** 每份总热量，单位：千卡。 */
    private BigDecimal calorie;
    /** 可能涉及的过敏原提示。 */
    private String allergenInfo;
    /** 菜谱及营养数据来源名称。 */
    private String sourceName;
    /** 数据来源的文献、页面或数据集引用。 */
    private String sourceRef;
    /** 系统菜谱数据版本，用于留档和升级。 */
    private String dataVersion;
    /** 构成该系统菜谱的食材及营养快照。 */
    private List<SystemRecipeIngredient> ingredients;
}
