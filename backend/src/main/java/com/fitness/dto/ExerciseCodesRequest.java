package com.fitness.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;

public record ExerciseCodesRequest(
        @Size(max = 100, message = "一次最多解析100个动作")
        List<@Pattern(regexp = "[A-Za-z0-9_-]{3,32}", message = "动作编号格式无效") String> exerciseCodes) {}
