package com.fitness.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "food_database")
public class FoodDatabase {
    /** 食材库记录的数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /** 食材标准名称。 */
    @Column(name = "food_name", unique = true, nullable = false, length = 50)
    private String foodName;

    /** 用于模糊搜索的常用别名，多个值以约定分隔符保存。 */
    @Column(name = "aliases", length = 255)
    private String aliases;

    /** 食材分类，如谷薯类、肉蛋类或蔬菜类。 */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** 营养数据对应的食用状态，如生重或熟重。 */
    @Column(name = "serving_state", nullable = false, length = 20)
    private String servingState;
    
    /** 每 100 克蛋白质含量，单位：克。 */
    @Column(name = "protein", nullable = false, precision = 10, scale = 2)
    private BigDecimal protein;
    
    /** 每 100 克碳水化合物含量，单位：克。 */
    @Column(name = "carb", nullable = false, precision = 10, scale = 2)
    private BigDecimal carb;
    
    /** 每 100 克脂肪含量，单位：克。 */
    @Column(name = "fat", nullable = false, precision = 10, scale = 2)
    private BigDecimal fat;
    
    /** 每 100 克热量，单位：千卡。 */
    @Column(name = "calorie", nullable = false, precision = 10, scale = 2)
    private BigDecimal calorie;

    /** 营养数据来源名称。 */
    @Column(name = "source_name", nullable = false, length = 50)
    private String sourceName;

    /** 数据来源中的原始编号或引用标识。 */
    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    /** 导入数据集版本，用于追溯和升级。 */
    @Column(name = "data_version", nullable = false, length = 30)
    private String dataVersion;

    /** 是否允许该食材在前端被搜索和选择。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 默认排序权重，数值越大越靠前。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
