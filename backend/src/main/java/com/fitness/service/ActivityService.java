package com.fitness.service;

import com.fitness.auth.WechatLoginService;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.ActivitySummary;
import com.fitness.dto.WeRunSyncRequest;
import com.fitness.entity.ActivityDailyRecord;
import com.fitness.entity.User;
import com.fitness.mapper.ActivityDailyRecordMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** R6：同步微信运动步数，并保存不参与能量差的独立估算参考。 */
@Service
@Transactional(readOnly = true)
public class ActivityService {
    static final String SOURCE = "WECHAT_WERUN";
    static final String ESTIMATION_VERSION = "STEP_WEIGHT_V1";
    static final BigDecimal STEP_WEIGHT_FACTOR = new BigDecimal("0.0005");
    private final ActivityDailyRecordMapper mapper;
    private final UserService userService;
    private final WechatLoginService wechatLoginService;
    private final WeRunCloudOpenDataService cloudOpenDataService;
    private final WeRunDataDecryptor decryptor;

    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone = "Asia/Shanghai";

    public ActivityService(ActivityDailyRecordMapper mapper, UserService userService,
                           WechatLoginService wechatLoginService,
                           WeRunCloudOpenDataService cloudOpenDataService,
                           WeRunDataDecryptor decryptor) {
        this.mapper = mapper;
        this.userService = userService;
        this.wechatLoginService = wechatLoginService;
        this.cloudOpenDataService = cloudOpenDataService;
        this.decryptor = decryptor;
    }

    @Transactional(rollbackFor = Exception.class)
    public ActivitySummary syncWechatSteps(Long userId, WeRunSyncRequest request,
                                           String cloudOpenid, String cloudAppId) {
        User user = requireUser(userId);
        WeRunDataDecryptor.WeRunPayload payload = StringUtils.hasText(request.cloudId())
                ? cloudOpenDataService.fetch(request.cloudId(), cloudOpenid, cloudAppId, user.getOpenid())
                : decryptTraditional(request, user);
        LocalDate today = today();
        LocalDate startDate = today.minusDays(30);
        Map<LocalDate, WeRunDataDecryptor.StepEntry> latestByDate = new LinkedHashMap<>();
        payload.stepInfoList().stream()
                .sorted(Comparator.comparingLong(WeRunDataDecryptor.StepEntry::timestamp))
                .forEach(entry -> {
                    LocalDate date = Instant.ofEpochSecond(entry.timestamp()).atZone(zone()).toLocalDate();
                    if (!date.isBefore(startDate) && !date.isAfter(today)) latestByDate.put(date, entry);
                });

        for (Map.Entry<LocalDate, WeRunDataDecryptor.StepEntry> item : latestByDate.entrySet()) {
            int steps = item.getValue().steps();
            if (steps < 0 || steps > 500_000) {
                throw new IllegalArgumentException("微信运动步数超出合理范围");
            }
            ActivityDailyRecord record = new ActivityDailyRecord();
            record.setUserId(userId);
            record.setActivityDate(item.getKey());
            record.setSteps(steps);
            record.setEstimatedCalories(estimateCalories(steps, user.getWeight()));
            record.setReferenceWeight(user.getWeight().setScale(2, RoundingMode.HALF_UP));
            record.setSource(SOURCE);
            record.setEstimationVersion(ESTIMATION_VERSION);
            record.setSourceTimestamp(item.getValue().timestamp());
            mapper.upsert(record);
        }
        return summary(userId);
    }

    private WeRunDataDecryptor.WeRunPayload decryptTraditional(WeRunSyncRequest request, User user) {
        WechatLoginService.WechatSession session;
        try {
            session = wechatLoginService.exchangeCodeForSession(request.code());
        } catch (BusinessException | IllegalArgumentException error) {
            throw error;
        } catch (Exception error) {
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "暂时无法连接微信运动，请稍后重试");
        }
        if (!user.getOpenid().equals(session.openid())) {
            throw new SecurityException("微信运动账号与当前登录账号不一致");
        }
        if (!StringUtils.hasText(session.sessionKey())) {
            throw new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                    "模拟登录不能读取真实微信运动，请配置小程序 AppID 后使用真机同步");
        }
        return decryptor.decrypt(session.sessionKey(), request.encryptedData(), request.iv());
    }

    public ActivitySummary summary(Long userId) {
        requireUser(userId);
        LocalDate today = today();
        LocalDate startDate = today.minusDays(30);
        List<ActivityDailyRecord> records = mapper.findByDateRange(userId, startDate, today);
        List<ActivitySummary.DailyPoint> points = records.stream()
                .map(row -> new ActivitySummary.DailyPoint(row.getActivityDate(), row.getSteps(),
                        row.getEstimatedCalories(), row.getReferenceWeight()))
                .toList();
        var lastSyncedAt = records.stream().map(ActivityDailyRecord::getSyncedAt)
                .filter(value -> value != null).max(Comparator.naturalOrder()).orElse(null);
        List<String> notices = new ArrayList<>();
        notices.add("热量按步数 × 同步时体重 × 0.0005 估算，仅供活动趋势参考。");
        notices.add("微信运动不会返回具体设备来源，步数可能来自手机或已接入微信运动的穿戴设备。");
        notices.add("估算消耗不写入训练记录，也不参与阶段分析中的能量差和目标判断。");
        return new ActivitySummary(startDate, today, SOURCE, "微信运动汇总步数",
                ESTIMATION_VERSION, "步数 × 同步时体重(kg) × 0.0005",
                lastSyncedAt, points.size(), points, notices);
    }

    @Transactional(rollbackFor = Exception.class)
    public int deleteWechatData(Long userId) {
        requireUser(userId);
        return mapper.deleteByUserIdAndSource(userId, SOURCE);
    }

    static BigDecimal estimateCalories(int steps, BigDecimal weight) {
        if (weight == null || weight.signum() <= 0) throw new IllegalArgumentException("用户体重无效");
        return BigDecimal.valueOf(steps).multiply(weight).multiply(STEP_WEIGHT_FACTOR)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private User requireUser(Long userId) {
        User user = userService.findById(userId);
        if (user == null) throw new BusinessException(ApiErrorCode.NOT_FOUND, "用户档案不存在");
        return user;
    }

    private LocalDate today() { return LocalDate.now(zone()); }
    private ZoneId zone() { return ZoneId.of(businessZone); }
}
