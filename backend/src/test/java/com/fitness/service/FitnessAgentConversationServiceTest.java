package com.fitness.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitness.ai.AgentClient;
import com.fitness.ai.AgentUserContextService;
import com.fitness.common.ApiErrorCode;
import com.fitness.common.BusinessException;
import com.fitness.dto.FitnessAgentDtos.PythonResponse;
import com.fitness.dto.FitnessAgentDtos.Citation;
import com.fitness.dto.WorkoutDraftResponse;
import com.fitness.entity.AiAgentMessage;
import com.fitness.entity.AiAgentSession;
import com.fitness.mapper.AiAgentConversationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FitnessAgentConversationServiceTest {
    @Mock AiAgentConversationMapper mapper;
    @Mock AgentClient agentClient;
    @Mock AgentUserContextService contexts;
    @Mock WorkoutDraftService workoutDraftService;
    private FitnessAgentConversationService service;
    private FitnessAgentConversationPersistenceService persistence;
    private AiAgentSession session;

    @BeforeEach
    void setUp() {
        persistence = new FitnessAgentConversationPersistenceService(mapper);
        service = new FitnessAgentConversationService(mapper, agentClient, contexts,
                workoutDraftService, persistence, new ObjectMapper().findAndRegisterModules(),
                "Asia/Hong_Kong");
        session = new AiAgentSession();
        session.setId(12L);
        session.setUserId(7L);
        session.setSessionId("00000000-0000-0000-0000-000000000001");
        session.setTitle("Fitness Agent");
        lenient().when(mapper.findOwnedSession(7L, session.getSessionId())).thenReturn(session);
        lenient().when(mapper.findRecentMessages(12L, 6)).thenReturn(List.of());
        lenient().when(contexts.issue(7L)).thenReturn("signed-current-user-context");
    }

    @Test
    void tomorrowRequestCreatesDraftForTomorrowAndPersistsBothMessagesAfterSuccess() {
        String requestId = "agent-message-1";
        when(agentClient.runFitnessAgent(any(), eq(requestId))).thenReturn(new PythonResponse(
                requestId, "草稿已准备好", "ollama", 0, List.of("get_user_profile"),
                "WORKOUT_DRAFT", "明天肩部训练45分钟", false, List.of()));
        when(workoutDraftService.createForConversation(eq(7L), any(LocalDate.class), anyString(),
                eq("把这个计划纳入训练草稿"), eq(requestId + "-draft")))
                .thenAnswer(invocation -> draft(invocation.getArgument(1)));
        when(mapper.findMessages(12L)).thenReturn(List.of());

        service.send(7L, session.getSessionId(), "把这个计划纳入训练草稿", requestId);

        ArgumentCaptor<LocalDate> date = ArgumentCaptor.forClass(LocalDate.class);
        ArgumentCaptor<String> workoutRequest = ArgumentCaptor.forClass(String.class);
        verify(workoutDraftService).createForConversation(eq(7L), date.capture(), workoutRequest.capture(),
                eq("把这个计划纳入训练草稿"), eq(requestId + "-draft"));
        org.junit.jupiter.api.Assertions.assertEquals(
                LocalDate.now(ZoneId.of("Asia/Hong_Kong")).plusDays(1), date.getValue());
        assertTrue(workoutRequest.getValue().contains("明天肩部训练45分钟"));
        assertTrue(workoutRequest.getValue().contains("把这个计划纳入训练草稿"));
        verify(mapper, org.mockito.Mockito.times(2)).insertMessage(any());
    }

    @Test
    void persistedReplyIsGroundedInFinalDraftInsteadOfAgentPreview() {
        String requestId = "agent-message-grounded";
        WorkoutDraftResponse finalDraft = new WorkoutDraftResponse(
                "draft-2", "READY", "ollama", "肩部训练计划", "请检查草稿", "只练肩，不要练胸",
                LocalDate.now(ZoneId.of("Asia/Hong_Kong")), 45, 7,
                List.of(new WorkoutDraftResponse.ExerciseDraft(
                        "SHOULDER_RAISE", "哑铃侧平举", "哑铃", "肩部", 4, 12, 40, 60, 7,
                        null, "EXTERNAL_LOAD", "MISSING", "待选择", null, "肩部训练")),
                List.of(), "确认后保存", 0,
                new WorkoutDraftResponse.NutritionPlan(
                        new BigDecimal("2500"), BigDecimal.ZERO, new BigDecimal("2500"),
                        new BigDecimal("700"), new BigDecimal("40"),
                        List.of(new WorkoutDraftResponse.MealDraft(
                                "早餐", "RECIPE_BREAKFAST", "系统早餐", new BigDecimal("700"),
                                new BigDecimal("40"), new BigDecimal("80"), new BigDecimal("20"),
                                "1人份", "")), "仅供建议"),
                List.of());
        when(workoutDraftService.createForConversation(eq(7L), any(LocalDate.class),
                eq("生成肩部训练"), eq("生成肩部训练"), eq(requestId + "-draft"))).thenReturn(finalDraft);
        when(mapper.findMessages(12L)).thenReturn(List.of());

        service.send(7L, session.getSessionId(), "生成肩部训练", requestId);

        ArgumentCaptor<AiAgentMessage> messages = ArgumentCaptor.forClass(AiAgentMessage.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertMessage(messages.capture());
        String reply = messages.getAllValues().get(1).getContent();
        assertTrue(reply.contains("哑铃侧平举"));
        assertTrue(reply.contains("系统早餐"));
        assertTrue(!reply.contains("哑铃卧推"));
        assertTrue(!reply.contains("燕麦粥"));
        verify(agentClient, never()).runFitnessAgent(any(), anyString());
    }

    @Test
    void adviceRequestCannotCreateDraftEvenWhenModelReturnsDraftAction() {
        String requestId = "agent-message-advice-only";
        when(agentClient.runFitnessAgent(any(), eq(requestId))).thenReturn(new PythonResponse(
                requestId, "已生成训练草稿，请查看并编辑草稿", "ollama", 0,
                List.of("get_recent_analysis"), "WORKOUT_DRAFT", "今天生成力量训练", false, List.of()));
        when(mapper.findMessages(12L)).thenReturn(List.of());

        service.send(7L, session.getSessionId(), "结合近期数据给我建议", requestId);

        verify(workoutDraftService, never()).create(anyLong(), any(), anyString(), any(), anyString());
        verify(workoutDraftService, never()).createForConversation(
                anyLong(), any(), anyString(), anyString(), anyString());
        ArgumentCaptor<AiAgentMessage> messages = ArgumentCaptor.forClass(AiAgentMessage.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertMessage(messages.capture());
        AiAgentMessage assistant = messages.getAllValues().get(1);
        assertTrue("NONE".equals(assistant.getActionType()));
        assertTrue(assistant.getActionPayload() == null);
        assertTrue(assistant.getContent().contains("没有创建或保存训练草稿"));
    }

    @Test
    void approvedKnowledgeCitationsArePersistedWithoutChangingWriteBoundary() {
        String requestId = "agent-message-citation";
        when(agentClient.runFitnessAgent(any(), eq(requestId))).thenReturn(new PythonResponse(
                requestId, "通用知识库依据：力量训练应循序渐进。", "ollama", 0,
                List.of("search_fitness_knowledge"), "NONE", null, false,
                List.of(new Citation("KB_ACSM_RESISTANCE_2026", "ACSM 抗阻训练摘要",
                        "American College of Sports Medicine", "https://pubmed.ncbi.nlm.nih.gov/41843416/",
                        "2026-position-stand-project-summary-v1"))));
        when(mapper.findMessages(12L)).thenReturn(List.of());

        service.send(7L, session.getSessionId(), "力量训练为什么要循序渐进？", requestId);

        ArgumentCaptor<AiAgentMessage> messages = ArgumentCaptor.forClass(AiAgentMessage.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertMessage(messages.capture());
        AiAgentMessage assistant = messages.getAllValues().get(1);
        assertTrue("NONE".equals(assistant.getActionType()));
        assertTrue(assistant.getActionPayload().contains("KB_ACSM_RESISTANCE_2026"));
        assertTrue(!assistant.getActionPayload().contains("userId"));
        verify(workoutDraftService, never()).createForConversation(
                anyLong(), any(), anyString(), anyString(), anyString());
        verify(mapper).findRecentMessages(12L, 6);
    }

    @Test
    void historicalMessagesRestoreStructuredCitations() {
        AiAgentMessage assistant = new AiAgentMessage();
        assistant.setMessageId("historical-citation-1");
        assistant.setRole("ASSISTANT");
        assistant.setContent("通用知识库依据：力量训练应循序渐进。");
        assistant.setProvider("ollama");
        assistant.setActionType("NONE");
        assistant.setActionPayload("""
                {"citations":[{
                  "documentId":"KB_ACSM_RESISTANCE_2026",
                  "title":"ACSM 抗阻训练摘要",
                  "sourceName":"American College of Sports Medicine",
                  "sourceUrl":"https://pubmed.ncbi.nlm.nih.gov/41843416/",
                  "version":"2026-position-stand-project-summary-v1"
                }]}
                """);
        assistant.setCreatedAt(LocalDateTime.of(2026, 10, 13, 9, 30));
        when(mapper.findMessages(12L)).thenReturn(List.of(assistant));

        var result = service.get(7L, session.getSessionId());

        assertEquals(1, result.messages().size());
        assertEquals("KB_ACSM_RESISTANCE_2026",
                result.messages().get(0).citations().get(0).documentId());
        assertEquals("https://pubmed.ncbi.nlm.nih.gov/41843416/",
                result.messages().get(0).citations().get(0).sourceUrl());
    }

    @Test
    void completedTodayReturnsActionableDateMessageInsteadOfUnusableDraftCard() {
        String requestId = "agent-message-completed-date";
        when(workoutDraftService.createForConversation(eq(7L), any(LocalDate.class),
                eq("帮我生成今天的肩部训练"), eq("帮我生成今天的肩部训练"),
                eq(requestId + "-draft"))).thenThrow(new IllegalArgumentException(
                "该日期训练已完成，不能再生成待确认计划；请明确选择明天或其他未来日期"));
        when(mapper.findMessages(12L)).thenReturn(List.of());

        service.send(7L, session.getSessionId(), "帮我生成今天的肩部训练", requestId);

        ArgumentCaptor<AiAgentMessage> messages = ArgumentCaptor.forClass(AiAgentMessage.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertMessage(messages.capture());
        AiAgentMessage assistant = messages.getAllValues().get(1);
        assertTrue("NONE".equals(assistant.getActionType()));
        assertTrue(assistant.getActionPayload() == null);
        assertTrue(assistant.getContent().contains("明天或其他未来日期"));
        verify(agentClient, never()).runFitnessAgent(any(), anyString());
    }

    @Test
    void clearDeletesOnlyMessagesFromOwnedSession() {
        when(mapper.deleteMessages(12L)).thenReturn(4);

        com.fitness.dto.FitnessAgentDtos.SessionResponse result = service.clear(7L, session.getSessionId());

        verify(mapper).deleteMessages(12L);
        verify(mapper).touchSession(12L);
        assertTrue(result.messages().isEmpty());
    }

    @Test
    void clearCannotDeleteAnotherUsersConversation() {
        assertThrows(BusinessException.class, () -> service.clear(8L, session.getSessionId()));

        verify(mapper, never()).deleteMessages(anyLong());
    }

    @Test
    void upstreamFailureDoesNotPersistHangingUserMessage() {
        when(agentClient.runFitnessAgent(any(), anyString())).thenThrow(
                new BusinessException(ApiErrorCode.UPSTREAM_UNAVAILABLE, "agent unavailable"));

        assertThrows(BusinessException.class, () -> service.send(
                7L, session.getSessionId(), "生成训练草稿", "agent-message-2"));

        verify(mapper, never()).insertMessage(any());
        verify(workoutDraftService, never()).create(anyLong(), any(), anyString(), any(), anyString());
    }

    private WorkoutDraftResponse draft(LocalDate date) {
        return new WorkoutDraftResponse(
                "draft-1", "DRAFT", "ollama", "肩部训练", "请检查草稿", "肩部训练45分钟",
                date, 45, 7, List.of(), List.of(), "确认后保存", 0, null, List.of());
    }
}
