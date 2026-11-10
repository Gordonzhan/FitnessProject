package com.fitness.service;

import com.fitness.entity.AiAgentMessage;
import com.fitness.mapper.AiAgentConversationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/** 将一轮成功对话原子落库，避免只留下用户消息或助手消息。 */
@Service
public class FitnessAgentConversationPersistenceService {
    private final AiAgentConversationMapper mapper;

    public FitnessAgentConversationPersistenceService(AiAgentConversationMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(rollbackFor = Exception.class)
    public void persistTurn(Long sessionDbId, String userContent, String assistantContent,
                            String provider, String toolSummary, String action, String payload) {
        insertMessage(sessionDbId, "USER", userContent, null, null, null, null);
        insertMessage(sessionDbId, "ASSISTANT", assistantContent, provider, toolSummary, action, payload);
        mapper.touchSession(sessionDbId);
    }

    private void insertMessage(Long sessionDbId, String role, String content, String provider,
                               String toolSummary, String action, String payload) {
        AiAgentMessage message = new AiAgentMessage();
        message.setMessageId(UUID.randomUUID().toString());
        message.setSessionDbId(sessionDbId);
        message.setRole(role);
        message.setContent(content);
        message.setProvider(provider);
        message.setToolSummary(toolSummary);
        message.setActionType(action);
        message.setActionPayload(payload);
        mapper.insertMessage(message);
    }
}
