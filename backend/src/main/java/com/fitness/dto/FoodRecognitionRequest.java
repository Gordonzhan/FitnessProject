package com.fitness.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

/** 小程序提交给 Java 的食物识别草稿请求；用户身份只能来自服务端认证上下文。 */
public record FoodRecognitionRequest(
        @NotEmpty(message = "请至少上传一张食物图片")
        @Size(max = 3, message = "单次最多识别3张图片")
        List<@Pattern(regexp = "^https://[^\\s]+$", message = "图片地址必须是HTTPS地址") String> imageUrls,
        @NotNull(message = "缺少用户营养上下文") @Valid UserContext userContext,
        @Size(max = 100, message = "食物提示不能超过100个字符") String dishHint) {

    public record UserContext(
            @Size(max = 40, message = "健身目标不能超过40个字符") String goal,
            @DecimalMin(value = "0", message = "每日热量目标不能为负数")
            @DecimalMax(value = "20000", message = "每日热量目标超出范围") BigDecimal dailyCalorieTarget,
            @DecimalMin(value = "0", message = "已摄入热量不能为负数")
            @DecimalMax(value = "50000", message = "已摄入热量超出范围") BigDecimal consumedCalories,
            @Size(max = 20, message = "饮食限制最多20项") List<@Size(max = 100) String> dietaryRestrictions) {}
}
