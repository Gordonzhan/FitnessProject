package com.fitness.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record TrainingPreferenceRequest(
        @NotNull(message = "请选择训练经验")
        @Pattern(regexp = "BEGINNER|INTERMEDIATE|ADVANCED", message = "训练经验参数无效")
        String experienceLevel,
        @NotNull(message = "请选择每周训练天数") @Min(value = 1, message = "每周至少训练1天")
        @Max(value = 7, message = "每周最多训练7天") Integer weeklyDays,
        @NotNull(message = "请填写单次训练时长") @Min(value = 10, message = "单次训练不能少于10分钟")
        @Max(value = 180, message = "单次训练不能超过180分钟") Integer sessionMinutes,
        @NotNull(message = "请选择训练地点")
        @Pattern(regexp = "HOME|GYM|OUTDOOR|FLEXIBLE", message = "训练地点参数无效")
        String trainingLocation,
        @NotNull(message = "请选择训练目标")
        @Pattern(regexp = "GENERAL_FITNESS|FAT_LOSS|MUSCLE_GAIN|STRENGTH|ENDURANCE|MOBILITY",
                message = "训练目标参数无效")
        String trainingGoal,
        @Size(max = 20, message = "可用器械最多20项")
        List<@Pattern(regexp = "[^,\\r\\n]{1,30}", message = "器械标签格式无效") String> equipmentTags,
        @Size(max = 10, message = "动作限制最多10项")
        List<@Pattern(regexp = "KNEE|LOWER_BACK|SHOULDER|WRIST|ANKLE|CARDIOVASCULAR|BALANCE|PREGNANCY",
                message = "动作限制标签无效") String> limitationTags,
        @Size(max = 50, message = "偏好动作最多50项")
        List<@Pattern(regexp = "[A-Z0-9_-]{3,32}", message = "偏好动作编号无效") String> preferredExerciseCodes,
        @Size(max = 50, message = "排除动作最多50项")
        List<@Pattern(regexp = "[A-Z0-9_-]{3,32}", message = "排除动作编号无效") String> excludedExerciseCodes,
        @NotNull(message = "请选择最低训练强度") @Min(value = 1, message = "RPE不能小于1")
        @Max(value = 10, message = "RPE不能大于10") Integer targetRpeMin,
        @NotNull(message = "请选择最高训练强度") @Min(value = 1, message = "RPE不能小于1")
        @Max(value = 10, message = "RPE不能大于10") Integer targetRpeMax) {}
