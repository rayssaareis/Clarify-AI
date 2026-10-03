package com.bureaucracytranslator.controller;

import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import com.bureaucracytranslator.exception.InvalidRequestException;
import com.bureaucracytranslator.exception.OcrServiceException;
import com.bureaucracytranslator.service.TranslateService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
        when(translateService.translate(anyString(), anyString(), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of("step 1")));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "Comparecer à audiência no dia 15/10.")
                        .param("targetLanguage", "original"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.explanation").value("explanation"));
    }

    @Test
    void targetLanguageEn_isAccepted() throws Exception {
        when(translateService.translate(anyString(), anyString(), anyString()))
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
        when(translateService.translate(anyString(), anyString(), anyString()))
                .thenThrow(new GeminiServiceException("Gemini API call failed"));

        mockMvc.perform(multipart("/api/translate")
                        .param("text", "some text")
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void imageFlow_emptyOcrResult_returns400() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(translateService.translateImage(any(), anyString(), anyString()))
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

        when(translateService.translateImage(any(), anyString(), anyString()))
                .thenThrow(new InvalidRequestException("The text extracted from the image is too long (max 8000 characters)."));

        mockMvc.perform(multipart("/api/translate")
                        .file(image)
                        .param("targetLanguage", "original"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void imageFlow_ocrUpstreamFailure_returns502() throws Exception {
        MockMultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});

        when(translateService.translateImage(any(), anyString(), anyString()))
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
}
