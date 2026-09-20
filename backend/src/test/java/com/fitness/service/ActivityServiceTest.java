package com.fitness.service;

import com.fitness.auth.WechatLoginService;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.WeRunSyncRequest;
import com.fitness.entity.ActivityDailyRecord;
import com.fitness.entity.User;
import com.fitness.mapper.ActivityDailyRecordMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityServiceTest {
    private final ActivityDailyRecordMapper mapper = mock(ActivityDailyRecordMapper.class);
    private final UserService users = mock(UserService.class);
    private final WechatLoginService wechat = mock(WechatLoginService.class);
    private final WeRunCloudOpenDataService cloudOpenData = mock(WeRunCloudOpenDataService.class);
    private final WeRunDataDecryptor decryptor = mock(WeRunDataDecryptor.class);
    private ActivityService service;
    private User user;

    @BeforeEach
    void setup() {
        service = new ActivityService(mapper, users, wechat, cloudOpenData, decryptor);
        ReflectionTestUtils.setField(service, "businessZone", "Asia/Shanghai");
        user = new User();
        user.setId(1L);
        user.setOpenid("openid-1");
        user.setWeight(new BigDecimal("70.00"));
        when(users.findById(1L)).thenReturn(user);
        when(wechat.exchangeCodeForSession("code"))
                .thenReturn(new WechatLoginService.WechatSession("openid-1", "session"));
        when(mapper.findByDateRange(eq(1L), any(), any())).thenReturn(List.of());
    }

    @Test
    void syncStoresOnlyRecentDatesWithVersionedWeightEstimate() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        when(decryptor.decrypt("session", "encrypted", "iv")).thenReturn(new WeRunDataDecryptor.WeRunPayload(List.of(
                entry(today.minusDays(31), 9000), entry(today.minusDays(1), 8000),
                entry(today, 8131), entry(today.plusDays(1), 100)), 1));

        service.syncWechatSteps(1L, new WeRunSyncRequest("code", null, "encrypted", "iv"), null, null);

        ArgumentCaptor<ActivityDailyRecord> captor = ArgumentCaptor.forClass(ActivityDailyRecord.class);
        verify(mapper, times(2)).upsert(captor.capture());
        assertEquals(List.of(today.minusDays(1), today),
                captor.getAllValues().stream().map(ActivityDailyRecord::getActivityDate).toList());
        assertEquals(new BigDecimal("280.00"), captor.getAllValues().get(0).getEstimatedCalories());
        assertEquals(new BigDecimal("284.59"), captor.getAllValues().get(1).getEstimatedCalories());
        assertEquals("STEP_WEIGHT_V1", captor.getAllValues().get(1).getEstimationVersion());
        assertEquals("WECHAT_WERUN", captor.getAllValues().get(1).getSource());
    }

    @Test
    void authenticatedAccountMustMatchWechatOpenDataAccount() {
        when(wechat.exchangeCodeForSession("code"))
                .thenReturn(new WechatLoginService.WechatSession("another-openid", "session"));
        assertThrows(SecurityException.class,
                () -> service.syncWechatSteps(1L,
                        new WeRunSyncRequest("code", null, "encrypted", "iv"), null, null));
        verifyNoInteractions(decryptor, mapper);
    }

    @Test
    void preservesActionableWechatUpstreamFailure() {
        BusinessException upstream = new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE,
                "微信小程序 AppID 或 AppSecret 配置不正确");
        when(wechat.exchangeCodeForSession("code")).thenThrow(upstream);

        BusinessException actual = assertThrows(BusinessException.class,
                () -> service.syncWechatSteps(1L,
                        new WeRunSyncRequest("code", null, "encrypted", "iv"), null, null));

        assertSame(upstream, actual);
        verifyNoInteractions(decryptor, mapper);
    }

    @Test
    void cloudIdUsesVerifiedCloudHostingOpenDataWithoutCodeSessionExchange() {
        LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
        var payload = new WeRunDataDecryptor.WeRunPayload(List.of(entry(today, 6000)), 1);
        when(cloudOpenData.fetch("cloud-id", "openid-1", "wx-app-id", "openid-1"))
                .thenReturn(payload);

        service.syncWechatSteps(1L, new WeRunSyncRequest(null, "cloud-id", null, null),
                "openid-1", "wx-app-id");

        verify(cloudOpenData).fetch("cloud-id", "openid-1", "wx-app-id", "openid-1");
        verifyNoInteractions(wechat, decryptor);
        verify(mapper).upsert(any(ActivityDailyRecord.class));
    }

    @Test
    void summaryExplicitlyKeepsEstimateOutsideEnergyDifference() {
        var summary = service.summary(1L);
        assertEquals("步数 × 同步时体重(kg) × 0.0005", summary.estimationFormula());
        assertEquals(3, summary.notices().size());
        assertEquals(true, summary.notices().get(2).contains("不参与阶段分析中的能量差"));
    }

    @Test
    void deletionIsAlwaysScopedToAuthenticatedUserAndWechatSource() {
        when(mapper.deleteByUserIdAndSource(1L, "WECHAT_WERUN")).thenReturn(31);
        assertEquals(31, service.deleteWechatData(1L));
        verify(mapper).deleteByUserIdAndSource(1L, "WECHAT_WERUN");
    }

    private WeRunDataDecryptor.StepEntry entry(LocalDate date, int steps) {
        return new WeRunDataDecryptor.StepEntry(
                date.atStartOfDay(ZoneId.of("Asia/Shanghai")).toEpochSecond(), steps);
    }
}
