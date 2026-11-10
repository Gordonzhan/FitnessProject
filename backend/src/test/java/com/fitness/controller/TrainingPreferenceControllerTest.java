package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.service.TrainingPreferenceService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TrainingPreferenceControllerTest {
    @Test
    void invalidPreferenceIsRejectedBeforeServiceCall() throws Exception {
        TrainingPreferenceService service = mock(TrainingPreferenceService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new TrainingPreferenceController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(post("/user/training-preferences")
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 3L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"experienceLevel":"EXPERT","weeklyDays":9,"sessionMinutes":5,
                                "trainingLocation":"HOME","trainingGoal":"GENERAL_FITNESS",
                                "equipmentTags":["无"],"limitationTags":[],
                                "preferredExerciseCodes":[],"excludedExerciseCodes":[],
                                "targetRpeMin":0,"targetRpeMax":11}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }
}
