package com.fitness.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "daily_intake")
public class DailyIntake {
    /** 单次饮食记录的数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    
    /** 饮食记录所属用户的数据库主键。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /** 计入能量统计的业务日期。 */
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    /** 用户菜谱的数据库主键；系统菜谱记录中为空。 */
    @Column(name = "recipe_id")
    @JsonIgnore
    private Long recipeDbId;

    /** 添加请求的幂等编号，用于阻止重复提交。 */
    @Column(name = "request_id", nullable = false, length = 64)
    @JsonIgnore
    private String requestId;

    /** 用户菜谱或系统菜谱统一使用的业务编号。 */
    @Column(name = "recipe_business_id", nullable = false, length = 50)
    private String recipeBusinessId;

    /** 添加饮食时保存的菜谱名称快照。 */
    @Column(name = "meal_type_snapshot", nullable = false, length = 20)
    private String mealTypeSnapshot;

    /** 添加饮食时保存的蛋白质快照，单位：克。 */
    @Column(name = "protein_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal proteinSnapshot;

    /** 添加饮食时保存的碳水化合物快照，单位：克。 */
    @Column(name = "carb_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal carbSnapshot;

    /** 添加饮食时保存的脂肪快照，单位：克。 */
    @Column(name = "fat_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal fatSnapshot;

    /** 添加饮食时保存的热量快照，单位：千卡。 */
    @Column(name = "calorie_snapshot", nullable = false, precision = 10, scale = 2)
    private BigDecimal calorieSnapshot;
    
    /** 饮食记录创建时间。 */
    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
    
    /** 对应的用户菜谱对象；系统菜谱饮食记录不建立该关联。 */
    @ManyToOne
    @JoinColumn(name = "recipe_id", insertable = false, updatable = false)
    @JsonIgnore
    private Recipe recipe;
}
