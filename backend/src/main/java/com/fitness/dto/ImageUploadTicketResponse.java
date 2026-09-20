package com.fitness.dto;

import java.time.Instant;
import java.util.Map;

public record ImageUploadTicketResponse(
        String ticketId,
        String host,
        String key,
        Instant expiresAt,
        Map<String, String> formFields) {
}
