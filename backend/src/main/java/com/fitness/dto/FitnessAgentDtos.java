package com.fitness.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

public final class FitnessAgentDtos {
    private FitnessAgentDtos() {}

    public record SendMessageRequest(@NotBlank @Size(max = 1000) String content) {}
    public record PythonMessage(String role, String content) {}
    public record PythonRequest(String requestId, String sessionId, String userContext,
                                List<PythonMessage> messages) {}
    public record PythonResponse(String requestId, String reply, String provider, Integer toolRounds,
                                 List<String> toolsUsed, String action, String workoutRequest,
                                 Boolean safetyNotice, List<Citation> citations) {}
    public record Citation(String documentId, String title, String sourceName, String sourceUrl,
                           String version) {}
    public record MessageResponse(String messageId, String role, String content, String provider,
                                  List<String> toolsUsed, String action, Object actionPayload,
                                  LocalDateTime createdAt, List<Citation> citations) {}
    public record SessionResponse(String sessionId, String title, List<MessageResponse> messages,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {}
    public record SearchRequest(@NotBlank @Size(max = 50) String keyword,
                                Integer limit) {}
}
