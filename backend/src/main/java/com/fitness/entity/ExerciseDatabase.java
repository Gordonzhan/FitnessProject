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

    /** 面向客户端和 Agent 的稳定业务编号，不使用数据库自增主键。 */
    @Column(name = "exercise_code", unique = true, nullable = false, length = 32)
    private String exerciseCode;
    
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

    /** 面向跟练展示的动作步骤说明。 */
    @Column(name = "instructions", columnDefinition = "TEXT")
    private String instructions;

    /** 动作执行时必须展示的安全提示。 */
    @Column(name = "safety_cues", columnDefinition = "TEXT")
    private String safetyCues;

    /** 常见错误，用于动作详情和后续计划校验说明。 */
    @Column(name = "common_mistakes", columnDefinition = "TEXT")
    private String commonMistakes;

    /** 标准限制标签，使用逗号分隔的稳定英文编码。 */
    @Column(name = "limitation_tags", length = 255)
    private String limitationTags;

    /** 可替代动作的稳定业务编号，使用逗号分隔。 */
    @Column(name = "alternative_exercise_codes", length = 500)
    private String alternativeExerciseCodes;

    @Column(name = "cover_url", length = 500)
    private String coverUrl;

    @Column(name = "media_url", length = 500)
    private String mediaUrl;

    @Column(name = "media_type", nullable = false, length = 20)
    private String mediaType;

    @Column(name = "default_sets")
    private Integer defaultSets;

    @Column(name = "default_reps")
    private Integer defaultReps;

    @Column(name = "default_duration_seconds")
    private Integer defaultDurationSeconds;

    @Column(name = "default_rest_seconds")
    private Integer defaultRestSeconds;

    @Column(name = "default_rpe")
    private Integer defaultRpe;

    @Column(name = "tempo", length = 20)
    private String tempo;

    /** 只有适合在跟练页面展示的动作才允许进入后续生成流程。 */
    @Column(name = "follow_along_enabled", nullable = false)
    private Boolean followAlongEnabled;
}
