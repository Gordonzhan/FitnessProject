package com.fitness.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ImageUploadTicketCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(ImageUploadTicketCleanupJob.class);
    private final OssDirectUploadService service;

    public ImageUploadTicketCleanupJob(OssDirectUploadService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${fitness.oss.upload-ticket-cleanup-delay-ms:60000}")
    public void cleanup() {
        try {
            int cleaned = service.cleanupExpiredTickets(100);
            if (cleaned > 0) log.info("IMAGE_UPLOAD_TICKET_EXPIRED_CLEANED count={}", cleaned);
        } catch (RuntimeException error) {
            log.warn("IMAGE_UPLOAD_TICKET_CLEANUP_FAILED reason={}", error.getClass().getSimpleName());
        }
    }
}
