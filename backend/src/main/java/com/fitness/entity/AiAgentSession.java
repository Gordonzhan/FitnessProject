package com.fitness.entity;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AiAgentSession {
    private Long id;
    private String sessionId;
    private Long userId;
    private String status;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
