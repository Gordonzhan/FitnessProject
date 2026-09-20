package com.fitness.controller;

import com.fitness.auth.AuthContext;
import com.fitness.service.ImageService;
import com.fitness.service.OssDirectUploadService;
import com.fitness.util.OSSUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImageControllerTest {
    private final OSSUtil oss = mock(OSSUtil.class);
    private final ImageService service = mock(ImageService.class);
    private final OssDirectUploadService directUploadService = mock(OssDirectUploadService.class);
    private MockMvc mvc;

    @BeforeEach void setup() {
        mvc = MockMvcBuilders.standaloneSetup(new ImageController(oss, service, directUploadService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test void deleteAcceptsJsonAndReturnsUnifiedResponse() throws Exception {
        mvc.perform(post("/image/delete").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"https://test/image.jpeg\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("success"));
        verify(service).deleteUnusedUpload(1L, "https://test/image.jpeg");
    }

    @Test void referencedImageReturnsConflictInsteadOfFakeSuccess() throws Exception {
        doThrow(new ImageService.ImageInUseException()).when(service).deleteUnusedUpload(1L, "https://test/image.jpeg");
        mvc.perform(post("/image/delete").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"imageUrl\":\"https://test/image.jpeg\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test void uploadReturnsUrlInsideData() throws Exception {
        when(oss.uploadImage(any(), eq(1L))).thenReturn("https://test/image.jpeg");
        mvc.perform(multipart("/image/upload").file(new MockMultipartFile("file", new byte[]{1}))
                        .requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.imageUrl").value("https://test/image.jpeg"));
    }

    @Test void ticketRequestValidatesSizeAndFormatBeforeCallingService() throws Exception {
        mvc.perform(post("/image/upload-ticket").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileSize\":0,\"format\":\"svg\",\"uploadAttemptId\":\"upl_attempt_123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(directUploadService);
    }

    @Test void confirmReturnsVerifiedUrlOnlyAfterServiceValidation() throws Exception {
        String ticketId = "977b707b-4444-41e3-aef9-9c58d0fa7b28";
        when(directUploadService.confirm(1L, ticketId)).thenReturn("https://test/image.jpeg");
        mvc.perform(post("/image/upload-confirm").requestAttr(AuthContext.USER_ID_ATTRIBUTE, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ticketId\":\"" + ticketId + "\",\"uploadAttemptId\":\"upl_attempt_123\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value("https://test/image.jpeg"));
    }
}
