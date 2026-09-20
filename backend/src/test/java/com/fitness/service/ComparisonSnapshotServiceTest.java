package com.fitness.service;

import com.fitness.dto.ComparisonDailySample;
import com.fitness.dto.ComparisonDailySource;
import com.fitness.mapper.ComparisonSnapshotMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ComparisonSnapshotServiceTest {
    private final ComparisonSnapshotMapper mapper = mock(ComparisonSnapshotMapper.class);
    private ComparisonSnapshotService service;

    @BeforeEach
    void setup() {
        service = new ComparisonSnapshotService(mapper);
        ReflectionTestUtils.setField(service, "businessZone", "Asia/Shanghai");
        ReflectionTestUtils.setField(service, "minimumUserEvents", 4);
    }

    @Test
    void disabledProductionSnapshotDoesNothing() {
        ReflectionTestUtils.setField(service, "enabled", false);
        assertEquals(0, service.refresh());
        verifyNoInteractions(mapper);
    }

    @Test
    void enabledSnapshotRequiresIndependentStrongSecret() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "hashSecret", "too-short");
        assertThrows(IllegalStateException.class, service::refresh);
        verifyNoInteractions(mapper);
    }

    @Test
    void eligibleDailyRowsAreHashedBeforeAnonymousUpsert() {
        ReflectionTestUtils.setField(service, "enabled", true);
        ReflectionTestUtils.setField(service, "hashSecret", "0123456789abcdef0123456789abcdef");
        ComparisonDailySource row = new ComparisonDailySource();
        row.setUserId(42L);
        row.setDisplayName("gordon");
        row.setSampleDate(LocalDate.of(2026, 9, 8));
        row.setGoal("maintain");
        row.setCompletedSessions(1);
        row.setPlannedSessions(0);
        row.setCancelledSessions(0);
        row.setActualCalories(new BigDecimal("320.00"));
        when(mapper.findEligibleDailyAggregates(any(), any(), eq(4))).thenReturn(List.of(row));

        assertEquals(1, service.refresh());

        ArgumentCaptor<ComparisonDailySample> captor = ArgumentCaptor.forClass(ComparisonDailySample.class);
        verify(mapper).deleteAnonymizedRange(any(), any(), eq("R7_REAL_V1"));
        verify(mapper).upsert(captor.capture());
        ComparisonDailySample sample = captor.getValue();
        assertEquals(64, sample.getParticipantKey().length());
        assertFalse(sample.getParticipantKey().contains("42"));
        assertEquals("g****n", sample.getMaskedDisplayName());
        assertEquals("ANONYMIZED", sample.getSource());
        assertEquals("R7_REAL_V1", sample.getDatasetVersion());
    }

    @Test
    void displayNameMaskSupportsLatinChineseAndMissingNames() {
        assertEquals("g****n", service.maskDisplayName("gordon"));
        assertEquals("王*明", service.maskDisplayName("王小明"));
        assertEquals("张*", service.maskDisplayName("张三"));
        assertEquals("训***者", service.maskDisplayName("  "));
    }
}
