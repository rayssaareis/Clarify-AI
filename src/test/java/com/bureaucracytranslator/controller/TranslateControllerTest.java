package com.bureaucracytranslator.controller;

import com.bureaucracytranslator.dto.SimplificationLevel;
import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import com.bureaucracytranslator.exception.InvalidRequestException;
import com.bureaucracytranslator.exception.OcrServiceException;
import com.bureaucracytranslator.service.TranslateService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Web-slice tests: TranslateService is mocked, so GeminiProperties and
 * OcrSpaceProperties are never instantiated and no environment variables
 * (GEMINI_API_KEY, OCR_SPACE_API_KEY) are needed to run this test class.
 */
@WebMvcTest(TranslateController.class)
class TranslateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private TranslateService translateService;

    @Test
    void textOnly_returnsOk() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "Comparecer à audiência no dia 15/10.")
                        .param("targetLanguage", "original"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("explanation"));
    }

    @Test
    void targetLanguageEn_isAccepted() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some document text")
                        .param("targetLanguage", "en"))
                .andExpect(status().isOk());
    }

    @Test
    void missingBothTextAndImage_returns400() throws Exception {
        mockMvc.perform(multipart("/api/translate")
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void bothTextAndImage_returns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("text", "some text")
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedImageType_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("image", "doc.pdf", "application/pdf", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/translate")
                        .file(file)
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageOverOneMegabyte_returns400() throws Exception {
        byte[] tooLarge = new byte[1_048_577]; // 1 MB + 1 byte
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", tooLarge);

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidTargetLanguage_returns400() throws Exception {
        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some text")
                        .param("targetLanguage", "fr"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void geminiServiceFailure_returns502() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenThrow(new GeminiServiceException("Gemini API call failed"));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some text")
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void imageFlow_emptyOcrResult_returns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(translateService.translateImage(any(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenThrow(new InvalidRequestException(
                        "Could not extract readable text from the image. Try a clearer photo or paste the text instead."));

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageFlow_ocrTextTooLong_returns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(translateService.translateImage(any(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenThrow(new InvalidRequestException("The text extracted from the image is too long (max 8000 characters)."));

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageFlow_ocrUpstreamFailure_returns502() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(translateService.translateImage(any(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenThrow(new OcrServiceException("OCR.space call failed"));

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadGateway());
    }
    @Test
    void wrongContentType_returns415() throws Exception {
        mockMvc.perform(post("/api/translate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnsupportedMediaType());
    }

    // ---------- simplification level ----------

    @ParameterizedTest
    @EnumSource(SimplificationLevel.class)
    void text_eachAcceptedLevel_isPassedToService(SimplificationLevel level) throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some document text")
                        .param("targetLanguage", "original")
                        .param("simplificationLevel", level.name()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("explanation"));

        verify(translateService).translate(eq("some document text"), eq("original"), eq(level), anyString());
    }

    @ParameterizedTest
    @EnumSource(SimplificationLevel.class)
    void image_eachAcceptedLevel_isPassedToService(SimplificationLevel level) throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(translateService.translateImage(any(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original")
                        .param("simplificationLevel", level.name()))
                .andExpect(status().isOk());

        verify(translateService).translateImage(any(), eq("original"), eq(level), anyString());
    }

    @Test
    void text_withoutLevel_usesDefaultLevel() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some document text")
                        .param("targetLanguage", "original"))
                .andExpect(status().isOk());

        verify(translateService).translate(
                eq("some document text"), eq("original"), eq(SimplificationLevel.CLEAR_DETAILED), anyString());
    }

    @Test
    void image_withoutLevel_usesDefaultLevel() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(translateService.translateImage(any(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original"))
                .andExpect(status().isOk());

        verify(translateService).translateImage(
                any(), eq("original"), eq(SimplificationLevel.CLEAR_DETAILED), anyString());
    }

    @Test
    void blankLevel_usesDefaultLevel() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some document text")
                        .param("simplificationLevel", "  "))
                .andExpect(status().isOk());

        verify(translateService).translate(
                eq("some document text"), eq("original"), eq(SimplificationLevel.CLEAR_DETAILED), anyString());
    }

    @Test
    void levelIsCaseInsensitive() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some document text")
                        .param("simplificationLevel", "in_depth"))
                .andExpect(status().isOk());

        verify(translateService).translate(
                eq("some document text"), eq("original"), eq(SimplificationLevel.IN_DEPTH), anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"EXPERT", "1", "QUICK", "QUICK SIMPLE", "null", "<script>"})
    void invalidLevel_returns400WithClearMessage_andNeverCallsService(String invalidLevel) throws Exception {
        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some document text")
                        .param("targetLanguage", "original")
                        .param("simplificationLevel", invalidLevel))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(
                        "Field 'simplificationLevel' must be one of: QUICK_SIMPLE, CLEAR_DETAILED, IN_DEPTH."));

        verifyNoInteractions(translateService);
    }

    @Test
    void invalidLevel_isRejectedForImageRequestsToo() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("simplificationLevel", "EXPERT"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(translateService);
    }

    @Test
    void geminiFailure_withExplicitLevel_stillReturns502() throws Exception {
        when(translateService.translate(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenThrow(new GeminiServiceException("Gemini API call failed"));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some text")
                        .param("simplificationLevel", "IN_DEPTH"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Could not process this document, please try again."));
    }
}
