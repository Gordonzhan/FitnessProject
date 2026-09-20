package com.fitness.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** R7 匿名人群对比响应，只暴露汇总口径、当前用户位置和脱敏榜单。 */
public record PopulationComparisonSummary(
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String cohortCode,
        String cohortLabel,
        int sampleSize,
        int minimumSampleSize,
        boolean demoData,
        List<Metric> metrics,
        Leaderboard leaderboard,
        List<String> notices) {

    public record Metric(
            String code,
            String label,
            BigDecimal currentValue,
            String unit,
            Integer percentile,
            String description) {}

    public record Leaderboard(
            String metricCode,
            String title,
            int currentRank,
            int totalParticipants,
            List<RankEntry> entries,
            String note) {}

    /** 仅包含已掩码昵称，不包含原名或任何账号标识。 */
    public record RankEntry(
            int rank,
            String displayName,
            String valueBand) {}
}
