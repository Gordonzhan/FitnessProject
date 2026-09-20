package com.fitness.audit;

import com.fitness.service.OperationLogService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AuditLogRetentionJobTest {
    private final OperationLogService logs = mock(OperationLogService.class);
    private final AuditLogRetentionJob job = new AuditLogRetentionJob(logs);

    @Test void retentionBelowThirtyDaysIsClampedToThirty() {
        ReflectionTestUtils.setField(job, "retentionDays", 7);
        Instant earliest = Instant.now().minus(30, ChronoUnit.DAYS).minusSeconds(2);
        Instant latest = Instant.now().minus(30, ChronoUnit.DAYS).plusSeconds(2);

        job.cleanup();

        verify(logs).deleteExpiredBefore(argThat(cutoff -> {
            Instant value = cutoff.toInstant();
            return !value.isBefore(earliest) && !value.isAfter(latest);
        }));
    }

    @Test void zeroDisablesAutomaticCleanup() {
        ReflectionTestUtils.setField(job, "retentionDays", 0);
        job.cleanup();
        verifyNoInteractions(logs);
    }

    @Test void cleanupFailureNeverBreaksTheApplication() {
        ReflectionTestUtils.setField(job, "retentionDays", 90);
        when(logs.deleteExpiredBefore(any(Date.class))).thenThrow(new IllegalStateException("db down"));
        assertDoesNotThrow(job::cleanup);
    }
}
