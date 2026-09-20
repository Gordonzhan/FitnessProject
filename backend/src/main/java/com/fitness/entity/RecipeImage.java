package com.fitness.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Data
@Entity
@Table(name = "recipe_images")
public class RecipeImage {
    /** 数据库自增主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    
    /** 所属用户菜谱的数据库主键。 */
    @Column(name = "recipe_id", nullable = false)
    @JsonIgnore
    private Long recipeDbId;
    
    /** OSS 图片访问地址。 */
    @Column(name = "image_url", nullable = false, length = 255)
    private String imageUrl;
    
    /** 图片展示顺序，数值越小越靠前。 */
    @Column(name = "sort_order", nullable = false, columnDefinition = "INT DEFAULT 0")
    private Integer sortOrder;
    
    /** 所属菜谱对象，供 JPA 关联查询使用。 */
    @ManyToOne
    @JoinColumn(name = "recipe_id", insertable = false, updatable = false)
    @JsonIgnore
    private Recipe recipe;
}
