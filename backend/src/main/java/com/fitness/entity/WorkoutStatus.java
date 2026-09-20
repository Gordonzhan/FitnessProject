package com.fitness.entity;

/** 训练记录在计划到执行过程中的业务状态。 */
public enum WorkoutStatus {
    /** 已安排但尚未完成的训练计划。 */
    PLANNED,
    /** 已完成且实际热量可计入能量统计。 */
    COMPLETED,
    /** 已取消且不计入能量统计的训练计划。 */
    CANCELLED
}
