package com.bureaucracytranslator.service;

import com.bureaucracytranslator.client.GeminiClient;
import com.bureaucracytranslator.client.OcrSpaceClient;
import com.bureaucracytranslator.dto.SimplificationLevel;
import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import com.bureaucracytranslator.exception.InvalidRequestException;
import com.bureaucracytranslator.exception.OcrServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TranslateServiceTest {

    @Mock
    private GeminiClient geminiClient;

    @Mock
    private OcrSpaceClient ocrSpaceClient;

    private TranslateService translateService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        translateService = new TranslateService(geminiClient, ocrSpaceClient);
    }

    @Test
    void translate_withValidText_returnsGeminiResponse() {
        TranslateResponse expected = new TranslateResponse("explanation", List.of("step 1"));
        when(geminiClient.explain("some document text", "original", SimplificationLevel.CLEAR_DETAILED, "req-1")).thenReturn(expected);

        TranslateResponse result = translateService.translate("some document text", "original", SimplificationLevel.CLEAR_DETAILED, "req-1");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void translate_whenGeminiReturnsBlankExplanation_throwsGeminiServiceException() {
        when(geminiClient.explain(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("  ", List.of("step 1")));

        assertThatThrownBy(() -> translateService.translate("text", "original", SimplificationLevel.CLEAR_DETAILED, "req-1"))
                .isInstanceOf(GeminiServiceException.class);
    }

    @Test
    void translate_whenGeminiReturnsEmptyNextSteps_throwsGeminiServiceException() {
        when(geminiClient.explain(anyString(), anyString(), any(SimplificationLevel.class), anyString()))
                .thenReturn(new TranslateResponse("explanation", List.of()));

        assertThatThrownBy(() -> translateService.translate("text", "original", SimplificationLevel.CLEAR_DETAILED, "req-1"))
                .isInstanceOf(GeminiServiceException.class);
    }

    @Test
    void translateImage_withGoodOcrResult_flowsIntoGemini() {
        MultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        TranslateResponse expected = new TranslateResponse("explanation", List.of("step 1"));

        when(ocrSpaceClient.extractText(image, "req-2")).thenReturn("extracted document text");
        when(geminiClient.explain("extracted document text", "en", SimplificationLevel.IN_DEPTH, "req-2")).thenReturn(expected);

        TranslateResponse result = translateService.translateImage(image, "en", SimplificationLevel.IN_DEPTH, "req-2");

        assertThat(result).isEqualTo(expected);
    }

    @Test
    void translateImage_whenOcrReturnsBlankText_throwsInvalidRequestException() {
        MultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(ocrSpaceClient.extractText(any(), anyString())).thenReturn("   ");

        assertThatThrownBy(() -> translateService.translateImage(image, "original", SimplificationLevel.CLEAR_DETAILED, "req-3"))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(geminiClient);
    }

    @Test
    void translateImage_whenOcrTextExceedsMaxLength_throwsInvalidRequestException() {
        MultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        String tooLong = "a".repeat(TranslateService.MAX_TEXT_LENGTH + 1);
        when(ocrSpaceClient.extractText(any(), anyString())).thenReturn(tooLong);

        assertThatThrownBy(() -> translateService.translateImage(image, "original", SimplificationLevel.CLEAR_DETAILED, "req-4"))
                .isInstanceOf(InvalidRequestException.class);

        verifyNoInteractions(geminiClient);
    }

    @Test
    void translateImage_whenOcrClientFails_propagatesOcrServiceException() {
        MultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        when(ocrSpaceClient.extractText(any(), anyString()))
                .thenThrow(new OcrServiceException("OCR.space call failed"));

        assertThatThrownBy(() -> translateService.translateImage(image, "original", SimplificationLevel.CLEAR_DETAILED, "req-5"))
                .isInstanceOf(OcrServiceException.class);

        verifyNoInteractions(geminiClient);
    }

    @ParameterizedTest
    @EnumSource(SimplificationLevel.class)
    void translate_passesTheSelectedLevelToGemini(SimplificationLevel level) {
        TranslateResponse expected = new TranslateResponse("explanation", List.of("step 1"));
        when(geminiClient.explain("doc", "original", level, "req-6")).thenReturn(expected);

        TranslateResponse result = translateService.translate("doc", "original", level, "req-6");

        assertThat(result).isEqualTo(expected);
        verify(geminiClient).explain("doc", "original", level, "req-6");
    }

    @ParameterizedTest
    @EnumSource(SimplificationLevel.class)
    void translateImage_passesTheSelectedLevelToGeminiAfterOcr(SimplificationLevel level) {
        MultipartFile image = new MockMultipartFile("image", "doc.jpg", "image/jpeg", new byte[]{1, 2, 3});
        TranslateResponse expected = new TranslateResponse("explanation", List.of("step 1"));
        when(ocrSpaceClient.extractText(image, "req-7")).thenReturn("ocr text");
        when(geminiClient.explain("ocr text", "original", level, "req-7")).thenReturn(expected);

        TranslateResponse result = translateService.translateImage(image, "original", level, "req-7");

        assertThat(result).isEqualTo(expected);
        verify(geminiClient).explain("ocr text", "original", level, "req-7");
    }
}
