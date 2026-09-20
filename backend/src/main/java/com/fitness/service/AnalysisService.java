package com.fitness.service;

import com.fitness.dto.AnalysisSummary;
import com.fitness.dto.DailyNutritionSummary;
import com.fitness.dto.DailyWorkoutSummary;
import com.fitness.entity.User;
import com.fitness.entity.WeightRecord;
import com.fitness.mapper.DailyIntakeMapper;
import com.fitness.mapper.WeightRecordMapper;
import com.fitness.mapper.WorkoutMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** R5 阶段分析：只读取饮食快照和已完成训练的实际消耗。 */
@Service
@Transactional(readOnly = true)
public class AnalysisService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private final DailyIntakeMapper dailyIntakeMapper;
    private final WorkoutMapper workoutMapper;
    private final WeightRecordMapper weightRecordMapper;
    private final UserService userService;

    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone = "Asia/Shanghai";

    public AnalysisService(DailyIntakeMapper dailyIntakeMapper,
                           WorkoutMapper workoutMapper,
                           WeightRecordMapper weightRecordMapper,
                           UserService userService) {
        this.dailyIntakeMapper = dailyIntakeMapper;
        this.workoutMapper = workoutMapper;
        this.weightRecordMapper = weightRecordMapper;
        this.userService = userService;
    }

    public AnalysisSummary analyze(Long userId, LocalDate startDate, LocalDate endDate) {
        validateRange(startDate, endDate);
        User user = userService.findById(userId);
        if (user == null) throw new IllegalArgumentException("用户档案不存在");

        List<DailyNutritionSummary> nutritionRows = dailyIntakeMapper.aggregateByDateRange(userId, startDate, endDate);
        List<DailyWorkoutSummary> workoutRows = workoutMapper.aggregateByDateRange(userId, startDate, endDate);
        List<WeightRecord> weights = weightRecordMapper.findLatestByDateRange(userId, startDate, endDate);
        Map<LocalDate, DailyNutritionSummary> nutritionByDate = indexNutrition(nutritionRows);
        Map<LocalDate, DailyWorkoutSummary> workoutByDate = indexWorkouts(workoutRows);

        int totalDays = (int) ChronoUnit.DAYS.between(startDate, endDate) + 1;
        BigDecimal dailyTarget = money(userService.calculateDailyCalories(user));
        List<AnalysisSummary.DailyPoint> points = new ArrayList<>(totalDays);
        BigDecimal intakeSum = ZERO;
        BigDecimal expenditureSum = ZERO;
        BigDecimal energySum = ZERO;
        BigDecimal targetDifferenceSum = ZERO;
        BigDecimal proteinSum = ZERO;
        BigDecimal carbSum = ZERO;
        BigDecimal fatSum = ZERO;
        int nutritionDays = 0;
        int workoutDays = 0;
        int completed = 0;
        int planned = 0;
        int cancelled = 0;

        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            DailyNutritionSummary nutrition = nutritionByDate.get(date);
            DailyWorkoutSummary workout = workoutByDate.get(date);
            BigDecimal intake = value(nutrition == null ? null : nutrition.getCalories());
            BigDecimal expenditure = value(workout == null ? null : workout.getActualCalories());
            BigDecimal energyDifference = intake.subtract(expenditure).setScale(2, RoundingMode.HALF_UP);
            BigDecimal targetDifference = energyDifference.subtract(dailyTarget).setScale(2, RoundingMode.HALF_UP);
            BigDecimal protein = value(nutrition == null ? null : nutrition.getProtein());
            BigDecimal carb = value(nutrition == null ? null : nutrition.getCarb());
            BigDecimal fat = value(nutrition == null ? null : nutrition.getFat());
            boolean hasNutrition = nutrition != null && count(nutrition.getRecordCount()) > 0;
            boolean hasWorkout = workout != null && statusCount(workout) > 0;

            if (hasNutrition) {
                nutritionDays++;
                intakeSum = intakeSum.add(intake);
                energySum = energySum.add(energyDifference);
                targetDifferenceSum = targetDifferenceSum.add(targetDifference);
                proteinSum = proteinSum.add(protein);
                carbSum = carbSum.add(carb);
                fatSum = fatSum.add(fat);
            }
            if (hasWorkout) workoutDays++;
            expenditureSum = expenditureSum.add(expenditure);
            completed += count(workout == null ? null : workout.getCompletedCount());
            planned += count(workout == null ? null : workout.getPlannedCount());
            cancelled += count(workout == null ? null : workout.getCancelledCount());

            points.add(new AnalysisSummary.DailyPoint(date, intake, expenditure, energyDifference,
                    targetDifference, protein, carb, fat, hasNutrition, hasWorkout));
        }

        int totalWorkouts = completed + planned + cancelled;
        BigDecimal completionRate = totalWorkouts == 0 ? null
                : ratio(BigDecimal.valueOf(completed * 100L), totalWorkouts);
        AnalysisSummary.Averages averages = new AnalysisSummary.Averages(
                average(intakeSum, nutritionDays), average(expenditureSum, totalDays),
                average(energySum, nutritionDays), average(targetDifferenceSum, nutritionDays),
                average(proteinSum, nutritionDays), average(carbSum, nutritionDays),
                average(fatSum, nutritionDays), nutritionDays, workoutDays);
        List<AnalysisSummary.WeightPoint> weightPoints = weights.stream()
                .map(item -> new AnalysisSummary.WeightPoint(item.getRecordDate(), item.getWeight()))
                .toList();
        BigDecimal weightChange = weightPoints.size() < 2 ? null
                : weightPoints.get(weightPoints.size() - 1).weight().subtract(weightPoints.get(0).weight())
                .setScale(2, RoundingMode.HALF_UP);
        AnalysisSummary.MacroReference macroReference = macroReference(user);
        AnalysisSummary.GoalAssessment goalAssessment = assessGoal(
                user, nutritionDays, averages.targetDifference(), completionRate, weightChange);
        String dataState = dataState(nutritionDays, totalDays, totalWorkouts, weightPoints.size());
        List<String> notices = notices(dataState, nutritionDays, totalDays, weightPoints.size());

        return new AnalysisSummary(startDate, endDate, totalDays, dataState, points, weightPoints,
                averages, new AnalysisSummary.TrainingCompletion(completed, planned, cancelled, completionRate),
                macroReference, goalAssessment, notices);
    }

    @Transactional(rollbackFor = Exception.class)
    public WeightRecord recordWeight(Long userId, LocalDate date, BigDecimal weight) {
        if (date == null || weight == null || weight.compareTo(new BigDecimal("25")) < 0
                || weight.compareTo(new BigDecimal("350")) > 0) {
            throw new IllegalArgumentException("请提供有效的体重记录");
        }
        LocalDate today = today();
        if (date.isAfter(today)) throw new IllegalArgumentException("不能记录未来日期的体重");
        WeightRecord record = new WeightRecord();
        record.setUserId(userId);
        record.setRecordDate(date);
        record.setWeight(weight.setScale(2, RoundingMode.HALF_UP));
        record.setSource("MANUAL");
        weightRecordMapper.insert(record);
        if (date.equals(today)) userService.updateCurrentWeight(userId, record.getWeight());
        return record;
    }

    private void validateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) throw new IllegalArgumentException("请选择分析日期范围");
        if (startDate.isAfter(endDate)) throw new IllegalArgumentException("开始日期不能晚于结束日期");
        if (endDate.isAfter(today())) throw new IllegalArgumentException("分析结束日期不能晚于今天");
        long days = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        if (days > 90) throw new IllegalArgumentException("单次分析最多支持90天");
    }

    private Map<LocalDate, DailyNutritionSummary> indexNutrition(List<DailyNutritionSummary> rows) {
        Map<LocalDate, DailyNutritionSummary> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getDate(), row));
        return result;
    }

    private Map<LocalDate, DailyWorkoutSummary> indexWorkouts(List<DailyWorkoutSummary> rows) {
        Map<LocalDate, DailyWorkoutSummary> result = new HashMap<>();
        rows.forEach(row -> result.put(row.getDate(), row));
        return result;
    }

    private AnalysisSummary.MacroReference macroReference(User user) {
        BigDecimal weight = user.getWeight();
        BigDecimal[] protein;
        BigDecimal[] carb;
        BigDecimal[] fat;
        if ("lose_weight".equals(user.getGoal())) {
            protein = range(weight, "1.6", "2.2");
            carb = range(weight, "2.0", "4.0");
            fat = range(weight, "0.6", "1.0");
        } else if ("gain_muscle".equals(user.getGoal())) {
            protein = range(weight, "1.6", "2.2");
            carb = range(weight, "3.0", "5.0");
            fat = range(weight, "0.8", "1.2");
        } else {
            protein = range(weight, "1.2", "1.8");
            carb = range(weight, "2.5", "4.5");
            fat = range(weight, "0.8", "1.2");
        }
        return new AnalysisSummary.MacroReference(weight,
                new AnalysisSummary.MacroRange(protein[0], protein[1], "g/天"),
                new AnalysisSummary.MacroRange(carb[0], carb[1], "g/天"),
                new AnalysisSummary.MacroRange(fat[0], fat[1], "g/天"),
                "按当前体重和健身目标估算，仅供健身参考；个体需求会随训练量和饮食结构变化。");
    }

    private AnalysisSummary.GoalAssessment assessGoal(User user, int nutritionDays,
                                                       BigDecimal targetDifference,
                                                       BigDecimal completionRate,
                                                       BigDecimal weightChange) {
        if (nutritionDays < 3) {
            return new AnalysisSummary.GoalAssessment("DATA_INSUFFICIENT", "数据不足",
                    "至少记录3天饮食后，才会结合阶段平均值判断与目标是否大致一致。",
                    weightChange, weightChange != null);
        }
        boolean energyAligned;
        boolean weightAligned = true;
        if ("lose_weight".equals(user.getGoal())) {
            energyAligned = targetDifference.compareTo(new BigDecimal("150")) <= 0;
            if (weightChange != null) weightAligned = weightChange.compareTo(new BigDecimal("0.30")) <= 0;
        } else if ("gain_muscle".equals(user.getGoal())) {
            energyAligned = targetDifference.compareTo(new BigDecimal("-150")) >= 0;
            if (weightChange != null) weightAligned = weightChange.compareTo(new BigDecimal("-0.30")) >= 0;
        } else {
            energyAligned = targetDifference.abs().compareTo(new BigDecimal("200")) <= 0;
            if (weightChange != null) weightAligned = weightChange.abs().compareTo(BigDecimal.ONE) <= 0;
        }
        boolean trainingAligned = completionRate == null || completionRate.compareTo(new BigDecimal("60")) >= 0;
        boolean aligned = energyAligned && trainingAligned && weightAligned;
        String status = aligned ? "ROUGHLY_ALIGNED" : "NEEDS_ATTENTION";
        String title = aligned ? "阶段表现与目标大致一致" : "阶段表现与目标存在偏差";
        String detail = "阶段净摄入相对当前目标日均"
                + signed(targetDifference) + " kcal；"
                + (completionRate == null ? "暂无训练计划可计算完成率" : "训练完成率 " + completionRate + "%")
                + (weightChange == null ? "；体重趋势数据不足" : "；体重变化 " + signed(weightChange) + " kg")
                + "。结论不依据单日记录，仅供健身参考。";
        return new AnalysisSummary.GoalAssessment(status, title, detail, weightChange, weightChange != null);
    }

    private String dataState(int nutritionDays, int totalDays, int totalWorkouts, int weightDays) {
        if (nutritionDays == 0 && totalWorkouts == 0) return "NO_DATA";
        int requiredNutritionDays = Math.max(3, (int) Math.ceil(totalDays * 0.7));
        return nutritionDays >= requiredNutritionDays && weightDays >= 2 ? "COMPLETE" : "PARTIAL";
    }

    private List<String> notices(String state, int nutritionDays, int totalDays, int weightDays) {
        List<String> notices = new ArrayList<>();
        if ("NO_DATA".equals(state)) notices.add(weightDays == 0
                ? "所选日期范围暂无饮食、训练或体重记录。"
                : "所选日期范围暂无饮食或训练记录，已有体重记录仍会保留。");
        if (nutritionDays < totalDays) notices.add("仅有 " + nutritionDays + "/" + totalDays + " 天饮食记录，记录可能不完整。");
        if (weightDays < 2) notices.add("体重记录不足2个不同日期，暂时无法判断体重趋势。");
        notices.add("分析使用保存饮食时的营养快照，结果仅供健身参考，不构成医疗诊断。");
        return notices;
    }

    private BigDecimal[] range(BigDecimal weight, String min, String max) {
        return new BigDecimal[]{weight.multiply(new BigDecimal(min)).setScale(0, RoundingMode.HALF_UP),
                weight.multiply(new BigDecimal(max)).setScale(0, RoundingMode.HALF_UP)};
    }

    private BigDecimal average(BigDecimal total, int divisor) {
        return divisor == 0 ? ZERO : total.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private BigDecimal ratio(BigDecimal numerator, int divisor) {
        return numerator.divide(BigDecimal.valueOf(divisor), 1, RoundingMode.HALF_UP);
    }

    private BigDecimal value(BigDecimal value) {
        return value == null ? ZERO : value.setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal money(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }

    private int count(Integer value) {
        return value == null ? 0 : value;
    }

    private int statusCount(DailyWorkoutSummary item) {
        return count(item.getCompletedCount()) + count(item.getPlannedCount()) + count(item.getCancelledCount());
    }

    private String signed(BigDecimal value) {
        return (value.signum() > 0 ? "+" : "") + value.stripTrailingZeros().toPlainString();
    }

    private LocalDate today() {
        return LocalDate.now(ZoneId.of(businessZone));
    }
}
