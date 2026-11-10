package com.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

/** 用户明确保存的训练偏好；自由文本病史不进入该结构。 */
@Data
public class UserTrainingPreference {
    private Long id;
    private Long userId;
    private String experienceLevel;
    private Integer weeklyDays;
    private Integer sessionMinutes;
    private String trainingLocation;
    private String trainingGoal;
    private String equipmentTags;
    private String limitationTags;
    private String preferredExerciseCodes;
    private String excludedExerciseCodes;
    private Integer targetRpeMin;
    private Integer targetRpeMax;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
