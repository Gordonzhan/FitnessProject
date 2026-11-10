package com.fitness.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/** 训练预计消耗的唯一确定性公式；AI 和普通编辑都只能提供动作与计划时长。 */
public final class WorkoutCalorieFormula {
    private static final int ACTIVE_SECONDS_PER_SET = 30;
    private static final int REST_SECONDS_BETWEEN_SETS = 60;

    private WorkoutCalorieFormula() {}

    public record Input(BigDecimal met, int sets) {}

    /** 首次生成计划时，将整场计划时长一次性分配为动作级时长快照。 */
    public static List<Integer> allocateDurationSeconds(int durationMinutes, List<Integer> sets) {
        if (durationMinutes < 1 || durationMinutes > 1440) throw new IllegalArgumentException("训练预计时长无效");
        if (sets == null || sets.isEmpty()) throw new IllegalArgumentException("请至少提供一个训练动作");
        List<Integer> referenceSeconds = sets.stream().map(WorkoutCalorieFormula::structuralDurationSeconds).toList();
        int totalReferenceSeconds = referenceSeconds.stream().mapToInt(Integer::intValue).sum();
        int plannedSeconds = durationMinutes * 60;
        int allocatedSeconds = 0;
        List<Integer> durations = new ArrayList<>();
        for (int index = 0; index < referenceSeconds.size(); index++) {
            int seconds = index == referenceSeconds.size() - 1
                    ? plannedSeconds - allocatedSeconds
                    : (int) Math.round(plannedSeconds
                    * (referenceSeconds.get(index) / (double) totalReferenceSeconds));
            allocatedSeconds += seconds;
            durations.add(seconds);
        }
        return List.copyOf(durations);
    }

    /** 组数变化时只按当前动作的结构时长比例调整，不重新分配其他动作的时长。 */
    public static int adjustDurationSeconds(int durationSeconds, int basisSets, int currentSets) {
        if (durationSeconds < 1 || durationSeconds > 86400) throw new IllegalArgumentException("动作计算时长无效");
        int before = structuralDurationSeconds(basisSets);
        int after = structuralDurationSeconds(currentSets);
        return Math.max(1, (int) Math.round(durationSeconds * (after / (double) before)));
    }

    public static BigDecimal calories(BigDecimal weight, BigDecimal met, int durationSeconds) {
        if (weight == null || weight.signum() <= 0) throw new IllegalArgumentException("用户体重无效，无法估算训练消耗");
        if (met == null || met.signum() <= 0) throw new IllegalArgumentException("动作 MET 无效，无法估算训练消耗");
        if (durationSeconds < 1 || durationSeconds > 86400) throw new IllegalArgumentException("动作计算时长无效");
        return met.multiply(weight).multiply(BigDecimal.valueOf(durationSeconds))
                .divide(BigDecimal.valueOf(3600), 1, RoundingMode.HALF_UP);
    }

    public static List<BigDecimal> allocate(BigDecimal weight, int durationMinutes, List<Input> inputs) {
        if (weight == null || weight.signum() <= 0) throw new IllegalArgumentException("用户体重无效，无法估算训练消耗");
        if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("请至少提供一个训练动作");
        List<Integer> durations = allocateDurationSeconds(durationMinutes,
                inputs.stream().map(Input::sets).toList());
        List<BigDecimal> calories = new ArrayList<>();
        for (int index = 0; index < inputs.size(); index++) {
            calories.add(calories(weight, inputs.get(index).met(), durations.get(index)));
        }
        return List.copyOf(calories);
    }

    public static int structuralDurationSeconds(int sets) {
        if (sets < 1 || sets > 100) throw new IllegalArgumentException("训练组数无效");
        return sets * ACTIVE_SECONDS_PER_SET + Math.max(0, sets - 1) * REST_SECONDS_BETWEEN_SETS;
    }

    public static int structuralDurationMinutes(List<Integer> sets) {
        if (sets == null || sets.isEmpty()) throw new IllegalArgumentException("请至少提供一个训练动作");
        int seconds = sets.stream().mapToInt(value -> {
            if (value == null) throw new IllegalArgumentException("训练组数无效");
            return structuralDurationSeconds(value);
        }).sum();
        return Math.max(1, (int) Math.ceil(seconds / 60.0));
    }
}
