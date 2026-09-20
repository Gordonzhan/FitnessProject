package com.fitness.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "users")
public class User {
    /** 数据库自增主键，仅用于服务端内部关联。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /** 微信用户唯一标识，用于登录身份映射。 */
    @Column(name = "openid", unique = true, nullable = false, length = 50)
    private String openid;

    /** 用户自填展示昵称；排行榜快照只保存该值的掩码结果。 */
    @Column(name = "display_name", length = 30)
    private String displayName;
    
    /** 性别编码，用于基础代谢率计算。 */
    @Column(name = "gender", nullable = false, length = 10)
    private String gender;
    
    /** 年龄，单位：周岁。 */
    @Column(name = "age", nullable = false)
    private Integer age;
    
    /** 身高，单位：厘米。 */
    @Column(name = "height", nullable = false, precision = 5, scale = 2)
    private BigDecimal height;

    /** 当前体重，单位：千克。 */
    @Column(name = "weight", nullable = false, precision = 5, scale = 2)
    private BigDecimal weight;

    /** 健身目标编码，如减脂、增肌或维持。 */
    @Column(name = "goal", nullable = false, length = 20)
    private String goal;

    /** 日常活动系数，用于由 BMR 推算 TDEE。 */
    @Column(name = "activity_level", nullable = false, precision = 5, scale = 3)
    private BigDecimal activityLevel;
    
    /** 用户档案创建时间。 */
    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
    
    /** 用户档案最后更新时间。 */
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
}
