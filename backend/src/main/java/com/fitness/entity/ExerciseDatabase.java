package com.fitness.entity;

import lombok.Data;
import jakarta.persistence.*;
import java.math.BigDecimal;

@Data
@Entity
@Table(name = "exercise_database")
public class ExerciseDatabase {
    /** 动作库记录的数据库主键。 */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    /** 训练动作标准名称。 */
    @Column(name = "name", unique = true, nullable = false, length = 50)
    private String name;

    /** 用于模糊搜索的动作别名。 */
    @Column(name = "aliases", length = 255)
    private String aliases;

    /** 动作训练类型分类。 */
    @Column(name = "category", nullable = false, length = 30)
    private String category;

    /** 主要目标肌群，多个肌群按约定分隔。 */
    @Column(name = "primary_muscles", nullable = false, length = 100)
    private String primaryMuscles;

    /** 完成动作所需器械。 */
    @Column(name = "equipment", nullable = false, length = 50)
    private String equipment;

    /** 动作默认强度等级。 */
    @Column(name = "intensity", nullable = false, length = 20)
    private String intensity;
    
    /** 代谢当量（MET），用于估算动作热量消耗。 */
    @Column(name = "met", nullable = false, precision = 3, scale = 1)
    private BigDecimal met;

    /** 动作数据来源名称。 */
    @Column(name = "source_name", nullable = false, length = 50)
    private String sourceName;

    /** 数据来源中的原始编号或引用标识。 */
    @Column(name = "source_ref", length = 100)
    private String sourceRef;

    /** 导入数据集版本，用于追溯和升级。 */
    @Column(name = "data_version", nullable = false, length = 30)
    private String dataVersion;

    /** 是否允许该动作在前端被搜索和选择。 */
    @Column(name = "enabled", nullable = false)
    private Boolean enabled;

    /** 默认排序权重，数值越大越靠前。 */
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;
}
