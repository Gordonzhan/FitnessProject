package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record NamesRequest(
        @NotNull(message = "名称列表不能为空") @Size(max = 100, message = "一次最多解析100个名称")
        List<@NotBlank(message = "名称不能为空") @Size(max = 50, message = "名称不能超过50个字符") String> names) {}
