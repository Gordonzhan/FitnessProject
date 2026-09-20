package com.fitness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ComparisonSnapshotJob {
    private static final Logger log = LoggerFactory.getLogger(ComparisonSnapshotJob.class);
    private final ComparisonSnapshotService service;

    public ComparisonSnapshotJob(ComparisonSnapshotService service) {
        this.service = service;
    }

    @Scheduled(cron = "${fitness.comparison.snapshot-cron:0 10 4 * * *}")
    public void refresh() {
        try {
            int rows = service.refresh();
            if (rows > 0) log.info("COMPARISON_SNAPSHOT_REFRESHED dailyRows={}", rows);
        } catch (RuntimeException error) {
            log.warn("COMPARISON_SNAPSHOT_FAILED reason={}", error.getClass().getSimpleName());
        }
    }
}
