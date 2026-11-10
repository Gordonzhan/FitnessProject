package com.fitness.controller;

import com.fitness.ai.AgentClient;
import com.fitness.auth.AuthContext;
import com.fitness.dto.AgentConnectionStatus;
import com.fitness.dto.FoodRecognitionResponse;
import com.fitness.service.FoodRecognitionDraftService;
import com.fitness.service.WorkoutDraftService;
import com.fitness.service.FitnessAgentConversationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AiControllerTest {
    private final AgentClient agentClient = mock(AgentClient.class);
    private final FoodRecognitionDraftService draftService = mock(FoodRecognitionDraftService.class);
    private final WorkoutDraftService workoutDraftService = mock(WorkoutDraftService.class);
    private final FitnessAgentConversationService conversationService = mock(FitnessAgentConversationService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AiController(
                        agentClient, draftService, workoutDraftService, conversationService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void statusExposesSafeAgentAvailability() throws Exception {
        when(agentClient.status("controller-test-001"))
                .thenReturn(AgentConnectionStatus.available("fitness-ai-agent", "0.1.0", "fake"));

        mvc.perform(get("/ai/status")
                        .requestAttr(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, "controller-test-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.available").value(true))
                .andExpect(jsonPath("$.data.provider").value("fake"));
    }

    @Test
    void recognitionReturnsAgentDraft() throws Exception {
        FoodRecognitionResponse response = new FoodRecognitionResponse(
                "controller-test-002", "SUCCEEDED", "fake", List.of(),
                new BigDecimal("0.82"), true, List.of(), null, "SKIPPED");
        when(agentClient.recognizeFood(any(), eq("controller-test-002"))).thenReturn(response);

        mvc.perform(post("/ai/food-recognition")
                        .requestAttr(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, "controller-test-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageUrls":["https://example.com/food.jpg"],
                                  "userContext":{"goal":"FAT_LOSS","dietaryRestrictions":[]},
                                  "dishHint":"鸡胸肉"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.requestId").value("controller-test-002"))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"));
    }

    @Test
    void invalidImageUrlIsRejectedBeforeAgentCall() throws Exception {
        mvc.perform(post("/ai/food-recognition")
                        .requestAttr(RequestTraceFilter.REQUEST_ID_ATTRIBUTE, "controller-test-003")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "imageUrls":["http://localhost/private.jpg"],
                                  "userContext":{"dietaryRestrictions":[]}
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(agentClient);
    }

    @Test
    void removeWorkoutExerciseUsesAuthenticatedUserAndCurrentDraft() throws Exception {
        mvc.perform(post("/ai/workout-draft/remove")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date":"2026-10-09",
                                  "draft":{"exercises":[{"exerciseCode":"EX_ONE"}]},
                                  "exerciseCode":"EX_ONE"
                                }
                                """))
                .andExpect(status().isOk());

        verify(workoutDraftService).remove(eq(7L), any(), any(), eq("EX_ONE"));
    }

    @Test
    void clearAgentMessagesUsesAuthenticatedUserAndOwnedSession() throws Exception {
        String sessionId = "00000000-0000-0000-0000-000000000001";

        mvc.perform(delete("/ai/agent/sessions/{sessionId}/messages", sessionId)
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk());

        verify(conversationService).clear(7L, sessionId);
    }
}
