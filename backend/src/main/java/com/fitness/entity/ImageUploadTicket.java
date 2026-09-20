package com.fitness.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ImageUploadTicket {
    private String ticketId;
    private Long userId;
    private String objectKey;
    private Long expectedSize;
    private String expectedFormat;
    private String expectedMime;
    private String status;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime confirmedAt;
}
