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

    /** 该动作热量公式使用的总秒数快照；组数编辑只调整本动作，不再挤占其他动作。 */
    @Column(name = "calorie_duration_seconds", nullable = false)
    private Integer calorieDurationSeconds;

    /** 仅用于接收编辑请求中的时长基准组数，不写入数据库也不返回客户端。 */
    @Transient
    @JsonIgnore
    private Integer calorieBasisSets;
    
    /** 所属训练对象，供 JPA 关联查询使用。 */
    @ManyToOne
    @JoinColumn(name = "workout_id", insertable = false, updatable = false)
    @JsonIgnore
    private Workout workout;
}
