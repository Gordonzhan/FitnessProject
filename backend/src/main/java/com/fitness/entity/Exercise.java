package com.fitness.entity;

import lombok.Data;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "exercises")
public class Exercise {
    /** 数据库自增主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;
    
    /** 所属训练的数据库主键。 */
    @Column(name = "workout_id", nullable = false)
    @JsonIgnore
    private Long workoutDbId;
    
    /** 动作名称快照。 */
    @Column(name = "name", nullable = false, length = 50)
    private String name;
    
    /** 训练负重，单位：千克。 */
    @Column(name = "weight", precision = 10, scale = 2)
    private BigDecimal weight;
    
    /** 完成组数。 */
    @Column(name = "sets", nullable = false)
    private Integer sets;
    
    /** 每组重复次数。 */
    @Column(name = "reps", nullable = false)
    private Integer reps;
    
    /** 该动作估算的热量消耗，单位：千卡。 */
    @Column(name = "calories", nullable = false, precision = 10, scale = 2)
    private BigDecimal calories;
    
    /** 所属训练对象，供 JPA 关联查询使用。 */
    @ManyToOne
    @JoinColumn(name = "workout_id", insertable = false, updatable = false)
    @JsonIgnore
    private Workout workout;
}
