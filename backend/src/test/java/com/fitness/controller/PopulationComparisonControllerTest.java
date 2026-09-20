package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.service.PopulationComparisonService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PopulationComparisonControllerTest {
    private final PopulationComparisonService service = mock(PopulationComparisonService.class);
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new PopulationComparisonController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void comparisonUsesOnlyAuthenticatedUserId() throws Exception {
        mvc.perform(get("/comparison/training")
                        .param("userId", "999")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(service).compareTraining(7L);
    }
}
