package com.fitness.controller;

import com.fitness.ai.AgentUserContextService;
import com.fitness.dto.FitnessAgentDtos.SearchRequest;
import com.fitness.service.AgentReadToolService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InternalAiControllerTest {
    private final AgentUserContextService contexts = new AgentUserContextService(
            "0123456789abcdef0123456789abcdef", 300);
    private final AgentReadToolService tools = mock(AgentReadToolService.class);
    private final InternalAiController controller = new InternalAiController(contexts, tools,
            "0123456789abcdef0123456789abcdef");

    @Test
    void signedContextBindsToolQueryToIssuedUser() {
        MockHttpServletRequest request = request(contexts.issue(42L),
                "Bearer 0123456789abcdef0123456789abcdef");
        when(tools.profile(42L)).thenReturn(Map.of("goal", "lose_weight"));

        assertEquals(200, controller.profile(request).getCode());
        verify(tools).profile(42L);
    }

    @Test
    void rejectsInvalidServiceTokenBeforeReadingUserData() {
        MockHttpServletRequest request = request(contexts.issue(42L), "Bearer wrong");
        assertThrows(SecurityException.class, () -> controller.profile(request));
    }

    @Test
    void rejectsTamperedUserContext() {
        String valid = contexts.issue(42L);
        MockHttpServletRequest request = request("A" + valid.substring(1),
                "Bearer 0123456789abcdef0123456789abcdef");
        assertThrows(SecurityException.class, () -> controller.profile(request));
    }

    @Test
    void publicExerciseCatalogRequiresOnlyServiceIdentity() {
        MockHttpServletRequest request = request(null,
                "Bearer 0123456789abcdef0123456789abcdef");
        when(tools.searchExercises("深蹲", 3)).thenReturn(Map.of("items", java.util.List.of()));

        assertEquals(200, controller.publicExercises(new SearchRequest("深蹲", 3), request).getCode());
        verify(tools).searchExercises("深蹲", 3);
    }

    @Test
    void publicExerciseCatalogRejectsInvalidServiceIdentity() {
        MockHttpServletRequest request = request(null, "Bearer wrong");

        assertThrows(SecurityException.class,
                () -> controller.publicExercises(new SearchRequest("深蹲", 3), request));
    }

    private MockHttpServletRequest request(String context, String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (context != null) request.addHeader("X-Agent-User-Context", context);
        request.addHeader("Authorization", authorization);
        return request;
    }
}
