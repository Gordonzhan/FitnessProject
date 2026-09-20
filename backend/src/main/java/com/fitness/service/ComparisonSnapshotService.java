package com.fitness.service;

import com.fitness.dto.ComparisonDailySample;
import com.fitness.dto.ComparisonDailySource;
import com.fitness.mapper.ComparisonSnapshotMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HexFormat;
import java.util.List;

/** 生产可选任务：把真实训练先按日聚合，再以 HMAC 参与者键写入独立匿名表。 */
@Service
public class ComparisonSnapshotService {
    static final String DATASET_VERSION = "R7_REAL_V1";
    private final ComparisonSnapshotMapper mapper;

    @Value("${fitness.business-zone:Asia/Shanghai}")
    private String businessZone = "Asia/Shanghai";

    @Value("${fitness.comparison.real-snapshot-enabled:false}")
    private boolean enabled;

    @Value("${fitness.comparison.hash-secret:}")
    private String hashSecret;

    @Value("${fitness.comparison.minimum-user-events:4}")
    private int minimumUserEvents = 4;

    public ComparisonSnapshotService(ComparisonSnapshotMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public int refresh() {
        if (!enabled) return 0;
        if (hashSecret == null || hashSecret.length() < 32) {
            throw new IllegalStateException("R7匿名快照密钥至少需要32个字符");
        }
        LocalDate endDate = LocalDate.now(ZoneId.of(businessZone));
        LocalDate startDate = endDate.minusDays(29);
        List<ComparisonDailySource> rows = mapper.findEligibleDailyAggregates(
                startDate, endDate, Math.max(1, minimumUserEvents));
        mapper.deleteAnonymizedRange(startDate, endDate, DATASET_VERSION);
        for (ComparisonDailySource row : rows) mapper.upsert(toAnonymous(row));
        return rows.size();
    }

    ComparisonDailySample toAnonymous(ComparisonDailySource row) {
        ComparisonDailySample sample = new ComparisonDailySample();
        sample.setParticipantKey(hmac(row.getUserId()));
        sample.setMaskedDisplayName(maskDisplayName(row.getDisplayName()));
        sample.setSampleDate(row.getSampleDate());
        sample.setGoal(row.getGoal());
        sample.setCompletedSessions(row.getCompletedSessions());
        sample.setPlannedSessions(row.getPlannedSessions());
        sample.setCancelledSessions(row.getCancelledSessions());
        sample.setActualCalories(row.getActualCalories());
        sample.setSource("ANONYMIZED");
        sample.setDatasetVersion(DATASET_VERSION);
        return sample;
    }

    /** 按 Unicode 字符保留首尾，快照表永远不接收原始昵称。 */
    String maskDisplayName(String displayName) {
        if (displayName == null || displayName.isBlank()) return "训***者";
        int[] points = displayName.trim().codePoints().toArray();
        if (points.length == 0) return "训***者";
        String first = new String(points, 0, 1);
        if (points.length <= 2) return first + "*";
        String last = new String(points, points.length - 1, 1);
        return first + "*".repeat(points.length - 2) + last;
    }

    private String hmac(Long userId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(hashSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(("comparison:" + userId).getBytes(StandardCharsets.UTF_8)));
        } catch (Exception error) {
            throw new IllegalStateException("无法生成R7匿名参与者标识", error);
        }
    }
}
