package com.fitness.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "ingredients")
public class Ingredient {
    /** 数据库自增主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    
    /** 所属用户菜谱的数据库主键。 */
    @Column(name = "recipe_id", nullable = false)
    @JsonIgnore
    private Long recipeDbId;
    
    /** 食材名称快照。 */
    @Column(name = "food_name", nullable = false, length = 50)
    private String foodName;
    
    /** 食材用量，单位：克。 */
    @Column(name = "weight", nullable = false, precision = 10, scale = 2)
    private BigDecimal weight;

    /** 当前用量对应的蛋白质，单位：克。 */
    @Column(name = "protein", nullable = false, precision = 10, scale = 2)
    private BigDecimal protein;

    /** 当前用量对应的碳水化合物，单位：克。 */
    @Column(name = "carb", nullable = false, precision = 10, scale = 2)
    private BigDecimal carb;

    /** 当前用量对应的脂肪，单位：克。 */
    @Column(name = "fat", nullable = false, precision = 10, scale = 2)
    private BigDecimal fat;

    /** 当前用量对应的热量，单位：千卡。 */
    @Column(name = "calorie", nullable = false, precision = 10, scale = 2)
    private BigDecimal calorie;
    
    /** 所属菜谱对象，供 JPA 关联查询使用。 */
    @ManyToOne
    @JoinColumn(name = "recipe_id", insertable = false, updatable = false)
    @JsonIgnore
    private Recipe recipe;
}
