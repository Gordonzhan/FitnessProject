package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.service.AnalysisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AnalysisControllerTest {
    private final AnalysisService analysis = mock(AnalysisService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new AnalysisController(analysis))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void summaryUsesAuthenticatedUserAndExplicitDateRange() throws Exception {
        mvc.perform(post("/analysis/summary").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-07\",\"userId\":999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        verify(analysis).analyze(7L, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 7));
    }

    @Test
    void malformedRangeAndWeightAreRejectedBeforeService() throws Exception {
        mvc.perform(post("/analysis/summary").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/analysis/weight").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-07\",\"weight\":10}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(analysis);
    }

    @Test
    void manualWeightKeepsBusinessDateAndDecimalValue() throws Exception {
        mvc.perform(post("/analysis/weight").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 7L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-06\",\"weight\":68.55}"))
                .andExpect(status().isOk());
        verify(analysis).recordWeight(7L, LocalDate.of(2026, 9, 6), new BigDecimal("68.55"));
    }
}
