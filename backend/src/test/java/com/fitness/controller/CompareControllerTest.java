package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.dto.DailyIntakeItem;
import com.fitness.service.CompareService;
import com.fitness.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class CompareControllerTest {
    private final CompareService compare = mock(CompareService.class);
    private final UserService users = mock(UserService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new CompareController(compare, users))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void removalUsesAuthenticatedUserAndSpecificIntakeId() throws Exception {
        mvc.perform(post("/compare/remove-intake").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-05\",\"intakeId\":\"101\",\"userId\":999}"))
                .andExpect(jsonPath("$.code").value(200));
        verify(compare).removeDailyIntake(1L, LocalDate.of(2026, 9, 5), 101L);
    }

    @Test void invalidRequestNeverReachesRemoval() throws Exception {
        for (String body : List.of("{}", "{\"date\":\"bad\",\"intakeId\":\"101\"}",
                "{\"date\":\"2026-09-05\",\"intakeId\":\"bad\"}")) {
            mvc.perform(post("/compare/remove-intake").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                    .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }
        verifyNoInteractions(compare);
    }

    @Test void dailyDataIncludesSeparateIdsForRepeatedRecipes() throws Exception {
        var first = new DailyIntakeItem("101", "recipe-business-id", "鸡胸饭", BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("300"));
        var second = new DailyIntakeItem("102", "recipe-business-id", "鸡胸饭", BigDecimal.TEN,
                BigDecimal.TEN, BigDecimal.TEN, new BigDecimal("300"));
        when(compare.getSelectedRecipes(1L, LocalDate.of(2026, 9, 5))).thenReturn(List.of(first, second));
        mvc.perform(post("/compare/get-daily-data").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"date\":\"2026-09-05\"}"))
                .andExpect(jsonPath("$.data.selectedRecipes[0].intakeId").value("101"))
                .andExpect(jsonPath("$.data.selectedRecipes[1].intakeId").value("102"))
                .andExpect(jsonPath("$.data.selectedRecipes[0].recipeId").value("recipe-business-id"));
    }
}
