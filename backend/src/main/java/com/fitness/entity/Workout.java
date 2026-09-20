package com.fitness.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Entity
@Table(name = "workouts")
public class Workout {
    /** 数据库自增主键，仅用于关联训练动作。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    
    /** 训练所属用户的数据库主键。 */
    @Column(name = "user_id", nullable = false)
    private Long userId;
    
    /** 对外使用的训练业务编号。 */
    @Column(name = "workout_id", nullable = false, length = 50)
    private String workoutId;
    
    /** 训练计划或实际发生的业务日期。 */
    @Column(name = "date", nullable = false)
    private LocalDate date;
    
    /** 兼容旧版本的热量字段；新逻辑应根据状态读取预计或实际热量。 */
    @Column(name = "total_calories", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalCalories;

    /** 训练状态：计划中、已完成或已取消。 */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkoutStatus status;

    /** 根据动作、重量、组数和次数估算的消耗，单位：千卡。 */
    @Column(name = "estimated_calories", nullable = false, precision = 10, scale = 2)
    private BigDecimal estimatedCalories;

    /** 用户确认完成后的实际消耗，单位：千卡；未完成时为空。 */
    @Column(name = "actual_calories", precision = 10, scale = 2)
    private BigDecimal actualCalories;
    
    /** 训练记录创建时间。 */
    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
    
    /** 训练记录最后更新时间。 */
    @Column(name = "updated_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP")
    private LocalDateTime updatedAt;
    
    /** 本次训练包含的动作明细。 */
    @OneToMany(mappedBy = "workout", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private List<Exercise> exercises;
}
