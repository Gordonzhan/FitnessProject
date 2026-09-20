package com.fitness.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class SystemRecipeIngredient {
    /** 系统菜谱食材明细的数据库主键。 */
    @JsonIgnore
    private Long id;
    /** 所属系统菜谱模板的数据库主键。 */
    @JsonIgnore
    private Long templateDbId;
    /** 食材标准名称。 */
    private String foodName;
    /** 每份菜谱使用的食材重量，单位：克。 */
    private BigDecimal weight;
    /** 当前用量对应的蛋白质，单位：克。 */
    private BigDecimal protein;
    /** 当前用量对应的碳水化合物，单位：克。 */
    private BigDecimal carb;
    /** 当前用量对应的脂肪，单位：克。 */
    private BigDecimal fat;
    /** 当前用量对应的热量，单位：千卡。 */
    private BigDecimal calorie;
    /** 该食材营养数据的来源名称。 */
    private String sourceName;
    /** 数据来源中的原始编号或引用标识。 */
    private String sourceRef;
    /** 该食材快照采用的数据版本。 */
    private String dataVersion;
}
