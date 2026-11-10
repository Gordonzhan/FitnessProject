package com.fitness.service;

import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.entity.DailyIntake;
import com.fitness.dto.DailyIntakeItem;
import com.fitness.entity.Recipe;
import com.fitness.entity.Workout;
import com.fitness.entity.User;
import com.fitness.entity.SystemRecipeTemplate;
import com.fitness.mapper.DailyIntakeMapper;
import com.fitness.mapper.RecipeMapper;
import com.fitness.mapper.WorkoutMapper;
import com.fitness.mapper.SystemRecipeTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class CompareService {
    @Autowired
    private DailyIntakeMapper dailyIntakeMapper;
    @Autowired
    private RecipeMapper recipeMapper;
    @Autowired
    private WorkoutMapper workoutMapper;
    @Autowired
    private UserService userService;
    @Autowired
    private SystemRecipeTemplateMapper systemRecipeTemplateMapper;
    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone;
    
    /** 汇总用户指定日期的全部饮食热量快照，单位：千卡。 */
    public double calculateDailyIntake(Long userId, LocalDate date) {
        BigDecimal total = dailyIntakeMapper.sumCaloriesByUserIdAndDate(userId, date);
        return total == null ? 0 : total.doubleValue();
    }
    
    /** 汇总用户指定日期已完成训练的实际消耗，计划和取消记录不计入。 */
    public double calculateDailyExpenditure(Long userId, LocalDate date) {
        Workout workout = workoutMapper.findCompletedByDate(userId, date);
        if (workout == null || workout.getActualCalories() == null) return 0;
        return workout.getActualCalories().doubleValue();
    }

    /** 查询用户指定日期的训练记录，供能量对比页面展示状态。 */
    public Workout getWorkoutByDate(Long userId, LocalDate date) {
        return workoutMapper.findByDate(userId, date);
    }
    
    /** 使用服务端生成的幂等编号，将一份菜谱加入当日饮食。 */
    @Transactional(rollbackFor = Exception.class)
    public void addRecipeToDailyIntake(Long userId, LocalDate date, String recipeId) {
        addRecipeToDailyIntake(userId, date, recipeId, UUID.randomUUID().toString());
    }

    /**
     * 将一份用户菜谱或系统菜谱加入当日饮食，并保存当时的营养快照。
     * 相同请求编号重复提交时不会生成重复记录。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addRecipeToDailyIntake(Long userId, LocalDate date, String recipeId, String requestId) {
        addRecipeToDailyIntake(userId, date, recipeId, requestId, null, BigDecimal.ONE, null);
    }

    /**
     * 保存用户确认的餐次、份数和食用时间；营养快照按实际份数缩放。
     * 旧入口可省略这些字段并保持一份菜谱的历史行为。
     */
    @Transactional(rollbackFor = Exception.class)
    public void addRecipeToDailyIntake(Long userId, LocalDate date, String recipeId, String requestId,
                                       String mealSlot, BigDecimal servingCount, LocalTime consumedTime) {
        if (date == null || recipeId == null || recipeId.isBlank()
                || requestId == null || requestId.isBlank() || requestId.length() > 64) {
            throw new IllegalArgumentException("饮食记录参数无效");
        }
        LocalTime normalizedTime = consumedTime == null
                ? LocalTime.now(ZoneId.of(businessZone)).withSecond(0).withNano(0) : consumedTime;
        String normalizedSlot = mealSlot == null || mealSlot.isBlank()
                ? inferMealSlot(normalizedTime) : mealSlot;
        if (!List.of("UNSPECIFIED", "BREAKFAST", "LUNCH", "DINNER", "SNACK").contains(normalizedSlot)) {
            throw new IllegalArgumentException("餐次无效");
        }
        BigDecimal normalizedServing = servingCount == null ? BigDecimal.ONE : servingCount;
        if (normalizedServing.compareTo(new BigDecimal("0.25")) < 0
                || normalizedServing.compareTo(BigDecimal.TEN) > 0) {
            throw new IllegalArgumentException("食用份数必须在0.25到10之间");
        }
        // 根据 recipe_id 查询菜谱，获取自增主键 id
        Recipe recipe = recipeMapper.findByUserIdAndRecipeId(userId, recipeId);
        SystemRecipeTemplate template = recipe == null ? systemRecipeTemplateMapper.findByRecipeId(recipeId) : null;
        if (recipe == null && template == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "菜谱不存在");
        }
        
        DailyIntake dailyIntake = new DailyIntake();
        dailyIntake.setUserId(userId);
        dailyIntake.setDate(date);
        dailyIntake.setRecipeDbId(recipe == null ? null : recipe.getId());
        dailyIntake.setRequestId(requestId);
        dailyIntake.setRecipeBusinessId(recipeId);
        dailyIntake.setMealTypeSnapshot(recipe == null ? template.getMealType() : recipe.getMealType());
        dailyIntake.setMealSlot(normalizedSlot);
        dailyIntake.setServingCount(normalizedServing.setScale(2, RoundingMode.HALF_UP));
        dailyIntake.setConsumedTime(normalizedTime);
        dailyIntake.setProteinSnapshot(scaleSnapshot(
                recipe == null ? template.getProtein() : recipe.getProtein(), normalizedServing));
        dailyIntake.setCarbSnapshot(scaleSnapshot(
                recipe == null ? template.getCarb() : recipe.getCarb(), normalizedServing));
        dailyIntake.setFatSnapshot(scaleSnapshot(
                recipe == null ? template.getFat() : recipe.getFat(), normalizedServing));
        dailyIntake.setCalorieSnapshot(scaleSnapshot(
                recipe == null ? template.getCalorie() : recipe.getCalorie(), normalizedServing));
        dailyIntakeMapper.insertIfAbsent(dailyIntake);
    }

    private BigDecimal scaleSnapshot(BigDecimal value, BigDecimal servingCount) {
        return value.multiply(servingCount).setScale(2, RoundingMode.HALF_UP);
    }

    private String inferMealSlot(LocalTime time) {
        if (time.isBefore(LocalTime.of(10, 0))) return "BREAKFAST";
        if (time.isBefore(LocalTime.of(14, 0))) return "LUNCH";
        if (time.isBefore(LocalTime.of(19, 0))) return "DINNER";
        return "SNACK";
    }
    
    /** 删除指定日期中某菜谱产生的全部饮食记录，保留菜谱本身。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeRecipeFromDailyIntake(Long userId, LocalDate date, String recipeId) {
        // 根据 recipe_id 查询菜谱，获取自增主键 id
        Recipe recipe = recipeMapper.findByUserIdAndRecipeId(userId, recipeId);
        SystemRecipeTemplate template = recipe == null ? systemRecipeTemplateMapper.findByRecipeId(recipeId) : null;
        if (recipe == null && template == null) {
            throw new BusinessException(ApiErrorCode.NOT_FOUND, "菜谱不存在");
        }
        if (recipe != null) {
            dailyIntakeMapper.deleteByUserIdAndDateAndRecipeDbId(userId, date, recipe.getId());
        } else {
            dailyIntakeMapper.deleteByUserIdAndDateAndRecipeBusinessId(userId, date, recipeId);
        }
    }
    
    /** 按饮食记录主键删除用户当天的一条误加记录。 */
    @Transactional(rollbackFor = Exception.class)
    public void removeDailyIntake(Long userId, LocalDate date, Long intakeId) {
        if (date == null || intakeId == null || intakeId <= 0) {
            throw new IllegalArgumentException("请提供有效的日期和饮食记录编号");
        }
        // 限定本人、指定日期、单条记录；重复请求无副作用，不触碰菜谱及图片。
        dailyIntakeMapper.deleteByUserIdAndDateAndId(userId, date, intakeId);
    }

    /** 查询用户指定日期已选择的饮食明细及营养快照。 */
    public List<DailyIntakeItem> getSelectedRecipes(Long userId, LocalDate date) {
        List<DailyIntake> dailyIntakes = dailyIntakeMapper.findByUserIdAndDate(userId, date);
        return dailyIntakes.stream()
                .map(DailyIntakeItem::from)
                .toList();
    }
    
    /** 根据健身目标、建议摄入量和当日收支生成简短提示语。 */
    public String generateTipText(User user, double intake, double expenditure, double dailyCalories) {
        double difference = intake - expenditure;
        String goal = user.getGoal();
        
        if ("lose_weight".equals(goal)) {
            if (intake > dailyCalories + 300) {
                return "摄入超过推荐值" + String.format("%.2f", (intake / dailyCalories * 100 - 100)) + "%，不利于减脂";
            } else if (intake > dailyCalories - 200) {
                return "摄入适中，继续保持热量缺口";
            } else {
                return "热量缺口较大，注意不要过度节食";
            }
        } else if ("gain_muscle".equals(goal)) {
            if (intake < dailyCalories - 300) {
                return "摄入不足，建议增加" + String.format("%.2f", (dailyCalories - intake)) + "kcal 以促进增肌";
            } else if (intake < dailyCalories + 200) {
                return "摄入适中，有利于肌肉增长";
            } else {
                return "热量盈余较多，注意控制脂肪增长";
            }
        } else {
            if (difference > 500) {
                return "热量盈余较多，需注意控制饮食";
            } else if (difference > 0) {
                return "热量轻微盈余，保持适度运动";
            } else if (difference > -500) {
                return "热量轻微缺口，继续坚持";
            } else {
                return "热量缺口较大，注意补充营养";
            }
        }
    }
}
