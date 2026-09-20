package com.fitness.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UserUpdateRequest(
        @Size(max = 30, message = "展示昵称最多30个字符") String displayName,
        @NotNull(message = "请选择性别")
        @Pattern(regexp = "male|female", message = "性别参数无效") String gender,
        @NotNull(message = "请填写年龄") @Min(value = 12, message = "年龄不能小于12岁")
        @Max(value = 100, message = "年龄不能大于100岁") Integer age,
        @NotNull(message = "请填写身高") @DecimalMin(value = "100", message = "身高不能小于100厘米")
        @DecimalMax(value = "250", message = "身高不能大于250厘米") BigDecimal height,
        @NotNull(message = "请填写体重") @DecimalMin(value = "25", message = "体重不能小于25公斤")
        @DecimalMax(value = "350", message = "体重不能大于350公斤") BigDecimal weight,
        @NotNull(message = "请选择健身目标")
        @Pattern(regexp = "lose_weight|gain_muscle|maintain", message = "健身目标参数无效") String goal,
        @NotNull(message = "请选择活动水平") @DecimalMin(value = "1.2", message = "活动水平参数无效")
        @DecimalMax(value = "1.9", message = "活动水平参数无效") BigDecimal activityLevel,
        LocalDate weightRecordDate) {}
