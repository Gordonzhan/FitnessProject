package com.fitness.service;

import com.fitness.dto.ComparisonParticipantStats;
import com.fitness.dto.PopulationComparisonSummary;
import com.fitness.entity.User;
import com.fitness.mapper.ComparisonSampleMapper;
import com.fitness.mapper.WorkoutMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

/** R7：输出30天训练百分位及掩码昵称榜单，不提供原始身份或精确样本值。 */
@Service
@Transactional(readOnly = true)
public class PopulationComparisonService {
    private final ComparisonSampleMapper comparisonSampleMapper;
    private final WorkoutMapper workoutMapper;
    private final UserService userService;

    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone = "Asia/Shanghai";

    @Value("${fitness.comparison.minimum-sample-size:10}")
    private int minimumSampleSize = 10;

    @Value("${fitness.comparison.include-synthetic:false}")
    private boolean includeSynthetic;

    public PopulationComparisonService(ComparisonSampleMapper comparisonSampleMapper,
                                       WorkoutMapper workoutMapper,
                                       UserService userService) {
        this.comparisonSampleMapper = comparisonSampleMapper;
        this.workoutMapper = workoutMapper;
        this.userService = userService;
    }

    public PopulationComparisonSummary compareTraining(Long userId) {
        User user = userService.findById(userId);
        if (user == null) throw new IllegalArgumentException("用户档案不存在");

        LocalDate endDate = LocalDate.now(ZoneId.of(businessZone));
        LocalDate startDate = endDate.minusDays(29);
        ComparisonParticipantStats current = normalized(
                workoutMapper.aggregateComparisonStats(userId, startDate, endDate));
        List<ComparisonParticipantStats> all = comparisonSampleMapper.aggregateByRange(
                startDate, endDate, includeSynthetic);
        Cohort cohort = selectCohort(all, user.getGoal());
        boolean demoData = cohort.samples().stream().anyMatch(item -> "SYNTHETIC".equals(item.getSource()));
        boolean currentHasData = totalEvents(current) > 0;

        String status;
        if (cohort.samples().size() < minimumSampleSize) {
            status = "POPULATION_INSUFFICIENT";
        } else if (!currentHasData) {
            status = "CURRENT_DATA_INSUFFICIENT";
        } else {
            status = "READY";
        }

        List<PopulationComparisonSummary.Metric> metrics = metrics(
                current, cohort.samples(), "READY".equals(status));
        PopulationComparisonSummary.Leaderboard leaderboard = "READY".equals(status)
                ? leaderboard(current, cohort.samples()) : null;
        return new PopulationComparisonSummary(startDate, endDate, status, cohort.code(), cohort.label(),
                cohort.samples().size(), minimumSampleSize, demoData, metrics, leaderboard,
                notices(status, cohort, demoData));
    }

    private Cohort selectCohort(List<ComparisonParticipantStats> all, String goal) {
        List<ComparisonParticipantStats> sameGoal = all.stream()
                .filter(item -> goal != null && goal.equals(item.getGoal())).toList();
        if (sameGoal.size() >= minimumSampleSize) {
            return new Cohort("SAME_GOAL", "相同健身目标的活跃样本", sameGoal, false);
        }
        return new Cohort("ALL_ACTIVE", "全部健身目标的活跃样本", all, !sameGoal.isEmpty());
    }

    private List<PopulationComparisonSummary.Metric> metrics(ComparisonParticipantStats current,
                                                              List<ComparisonParticipantStats> samples,
                                                              boolean ready) {
        List<PopulationComparisonSummary.Metric> result = new ArrayList<>();
        BigDecimal sessions = decimal(current.getCompletedCount());
        BigDecimal activeDays = decimal(current.getActiveDays());
        BigDecimal completionRate = completionRate(current);
        result.add(metric("COMPLETED_SESSIONS", "完成训练", sessions, "次", ready,
                samples, item -> decimal(item.getCompletedCount()), "统计近30天状态为已完成的训练次数。"));
        result.add(metric("ACTIVE_DAYS", "实际训练天数", activeDays, "天", ready,
                samples, item -> decimal(item.getActiveDays()), "同一天多次训练仍按1个实际训练日计算。"));
        result.add(metric("PLAN_COMPLETION_RATE", "计划完成率", completionRate, "%", ready,
                samples, this::completionRate, "已完成 ÷ 已完成、计划中与已取消训练总数。"));
        return result;
    }

    private PopulationComparisonSummary.Metric metric(
            String code, String label, BigDecimal currentValue, String unit, boolean ready,
            List<ComparisonParticipantStats> samples,
            Function<ComparisonParticipantStats, BigDecimal> extractor, String description) {
        Integer percentile = ready ? percentile(currentValue, samples.stream().map(extractor).toList()) : null;
        return new PopulationComparisonSummary.Metric(code, label, currentValue, unit, percentile, description);
    }

    /** 百分位使用“达到或超过”的口径，平分者获得相同结果，结果固定为0～100整数。 */
    int percentile(BigDecimal current, List<BigDecimal> population) {
        if (population.isEmpty()) return 0;
        long atOrBelow = population.stream().filter(value -> value.compareTo(current) <= 0).count();
        return (int) Math.round(atOrBelow * 100.0 / population.size());
    }

    private PopulationComparisonSummary.Leaderboard leaderboard(
            ComparisonParticipantStats current, List<ComparisonParticipantStats> samples) {
        List<ComparisonParticipantStats> ordered = samples.stream()
                .sorted(Comparator.comparingInt(
                        (ComparisonParticipantStats item) -> value(item.getCompletedCount())).reversed())
                .limit(10)
                .toList();
        List<PopulationComparisonSummary.RankEntry> entries = ordered.stream()
                .map(item -> new PopulationComparisonSummary.RankEntry(
                        competitionRank(value(item.getCompletedCount()), samples),
                        maskedName(item.getMaskedDisplayName()),
                        sessionBand(value(item.getCompletedCount()))))
                .toList();
        int currentRank = competitionRank(value(current.getCompletedCount()), samples);
        return new PopulationComparisonSummary.Leaderboard(
                "COMPLETED_SESSIONS",
                "近30天完成训练次数榜",
                currentRank,
                samples.size() + 1,
                entries,
                "最多展示10个匿名样本；他人次数按区间展示，名次仅供健身参考。");
    }

    private int competitionRank(int completedCount, List<ComparisonParticipantStats> samples) {
        return 1 + (int) samples.stream()
                .filter(item -> value(item.getCompletedCount()) > completedCount)
                .count();
    }

    String sessionBand(int completedCount) {
        if (completedCount <= 0) return "0 次";
        if (completedCount <= 4) return "1–4 次";
        if (completedCount <= 8) return "5–8 次";
        if (completedCount <= 12) return "9–12 次";
        if (completedCount <= 16) return "13–16 次";
        if (completedCount <= 20) return "17–20 次";
        return "21 次以上";
    }

    private String maskedName(String value) {
        return value == null || value.isBlank() ? "训***者" : value;
    }

    private BigDecimal completionRate(ComparisonParticipantStats item) {
        int total = totalEvents(item);
        if (total == 0) return BigDecimal.ZERO.setScale(1);
        return decimal(item.getCompletedCount()).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);
    }

    private int totalEvents(ComparisonParticipantStats item) {
        return value(item.getCompletedCount()) + value(item.getPlannedCount()) + value(item.getCancelledCount());
    }

    private ComparisonParticipantStats normalized(ComparisonParticipantStats item) {
        return item == null ? new ComparisonParticipantStats() : item;
    }

    private List<String> notices(String status, Cohort cohort, boolean demoData) {
        List<String> notices = new ArrayList<>();
        if ("POPULATION_INSUFFICIENT".equals(status)) {
            notices.add("当前有效样本不足 " + minimumSampleSize + " 人，暂不计算百分位，避免产生误导。");
        } else if ("CURRENT_DATA_INSUFFICIENT".equals(status)) {
            notices.add("你在近30天暂无训练记录，记录至少一次训练后再进行人群对比。");
        }
        if (cohort.expanded()) notices.add("相同健身目标样本不足，已扩展为全部健身目标的活跃样本。");
        if (demoData) notices.add("当前包含本地 SYNTHETIC 演示样本，只用于功能验收，不能代表真实用户排名。");
        notices.add("榜单最多展示前10名，昵称只保留首尾字符（如 g****n）；他人训练次数只显示区间，不提供原名、账号标识或精确数值。");
        notices.add("百分位仅反映近30天记录在当前样本中的相对位置，仅供健身参考，不构成医疗诊断。");
        return notices;
    }

    private BigDecimal decimal(Integer value) {
        return BigDecimal.valueOf(value(value));
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private record Cohort(String code, String label, List<ComparisonParticipantStats> samples, boolean expanded) {}
}
