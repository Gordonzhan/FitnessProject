package com.fitness.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "recipes")
public class Recipe {
    /** 数据库自增主键，仅用于关联图片和食材。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    
    /** 菜谱所属用户的数据库主键。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /** 对外使用的菜谱业务编号。 */
    @Column(name = "recipe_id", nullable = false, length = 50)
    private String recipeId;
    
    /** 菜谱名称（历史字段名为 meal_type）。 */
    @Column(name = "meal_type", nullable = false, length = 20)
    private String mealType;
    
    /** 兼容旧数据的封面图片地址。 */
    @Column(name = "cover_image", length = 255)
    private String coverImage;
    
    /** 菜谱制作步骤。 */
    @Column(name = "steps", columnDefinition = "TEXT")
    private String steps;
    
    /** 每份蛋白质含量，单位：克。 */
    @Column(name = "protein", nullable = false, precision = 10, scale = 2)
    private BigDecimal protein;

    /** 每份碳水化合物含量，单位：克。 */
    @Column(name = "carb", nullable = false, precision = 10, scale = 2)
    private BigDecimal carb;

    /** 每份脂肪含量，单位：克。 */
    @Column(name = "fat", nullable = false, precision = 10, scale = 2)
    private BigDecimal fat;

    /** 每份总热量，单位：千卡。 */
    @Column(name = "calorie", nullable = false, precision = 10, scale = 2)
    private BigDecimal calorie;
    
    /** 菜谱创建时间。 */
    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
    
    /** 菜谱最后更新时间。 */
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
    
    /** 按顺序展示的菜谱图片列表。 */
    /** 菜谱所需食材及营养快照列表。 */
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<RecipeImage> images;
    
    @OneToMany(mappedBy = "recipe", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Ingredient> ingredients;
}
