package com.fitness.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiAgentMessage {
    private Long id;
    private String messageId;
    private Long sessionDbId;
    private String role;
    private String content;
    private String provider;
    private String toolSummary;
    private String actionType;
    private String actionPayload;
    private LocalDateTime createdAt;
}
