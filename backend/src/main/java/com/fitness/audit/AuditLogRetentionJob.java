package com.fitness.audit;

import com.fitness.service.OperationLogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
public class AuditLogRetentionJob {
    private static final Logger log = LoggerFactory.getLogger(AuditLogRetentionJob.class);
    private final OperationLogService operationLogService;

    @Value("${fitness.audit.retention-days:90}")
    private int retentionDays;

    public AuditLogRetentionJob(OperationLogService operationLogService) {
        this.operationLogService = operationLogService;
    }

    @Scheduled(cron = "${fitness.audit.cleanup-cron:0 20 3 * * *}")
    public void cleanup() {
        if (retentionDays <= 0) return;
        int safeRetentionDays = Math.max(30, Math.min(retentionDays, 365));
        Date cutoff = Date.from(Instant.now().minus(safeRetentionDays, ChronoUnit.DAYS));
        try {
            int deleted = operationLogService.deleteExpiredBefore(cutoff);
            if (deleted > 0) {
                log.info("AUDIT_LOG_RETENTION_COMPLETED deleted={} retentionDays={}",
                        deleted, safeRetentionDays);
            }
        } catch (RuntimeException error) {
            log.warn("AUDIT_LOG_RETENTION_FAILED retentionDays={} reason={}",
                    safeRetentionDays, error.getClass().getSimpleName());
        }
    }
}
