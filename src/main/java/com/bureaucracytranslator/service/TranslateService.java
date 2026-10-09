package com.bureaucracytranslator.service;

import com.bureaucracytranslator.client.GeminiClient;
import com.bureaucracytranslator.client.OcrSpaceClient;
import com.bureaucracytranslator.dto.SimplificationLevel;
import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import com.bureaucracytranslator.exception.InvalidRequestException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
public class TranslateService {

    /**
     * Shared with TranslateController: pasted text and OCR-extracted text are
     * both capped at the same limit before reaching Gemini.
     */
    public static final int MAX_TEXT_LENGTH = 8000;

    private final GeminiClient geminiClient;
    private final OcrSpaceClient ocrSpaceClient;

    public TranslateService(GeminiClient geminiClient, OcrSpaceClient ocrSpaceClient) {
        this.geminiClient = geminiClient;
        this.ocrSpaceClient = ocrSpaceClient;
    }

    public TranslateResponse translate(String text, String targetLanguage, SimplificationLevel level, String requestId) {
        TranslateResponse response = geminiClient.explain(text, targetLanguage, level, requestId);
        validate(response);
        return response;
    }

    /**
     * Extracts text from the image via OCR, then reuses the exact same
     * Gemini flow as translate(String, String, SimplificationLevel, String). Gemini never knows
     * whether the text originally came from a paste or an image.
     */
    public TranslateResponse translateImage(MultipartFile image, String targetLanguage,
                                            SimplificationLevel level, String requestId) {
        String extractedText = ocrSpaceClient.extractText(image, requestId);

        if (extractedText == null || extractedText.isBlank()) {
            throw new InvalidRequestException(
                    "Could not extract readable text from the image. Try a clearer photo or paste the text instead.");
        }
        if (extractedText.length() > MAX_TEXT_LENGTH) {
            throw new InvalidRequestException(
                    "The text extracted from the image is too long (max " + MAX_TEXT_LENGTH + " characters).");
        }

        return translate(extractedText, targetLanguage, level, requestId);
    }

    private void validate(TranslateResponse response) {
        if (response == null || response.explanation() == null || response.explanation().isBlank()) {
            throw new GeminiServiceException("Invalid response from Gemini: missing explanation");
        }

        List<String> nextSteps = response.nextSteps();
        boolean hasBlankStep = nextSteps != null
                && nextSteps.stream().anyMatch(step -> step == null || step.isBlank());

        if (nextSteps == null || nextSteps.isEmpty() || hasBlankStep) {
            throw new GeminiServiceException("Invalid response from Gemini: missing or malformed nextSteps");
        }
    }
}
