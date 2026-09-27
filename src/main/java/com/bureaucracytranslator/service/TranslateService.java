package com.bureaucracytranslator.service;

import com.bureaucracytranslator.client.GeminiClient;
import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TranslateService {

    private final GeminiClient geminiClient;

    public TranslateService(GeminiClient geminiClient) {
        this.geminiClient = geminiClient;
    }

    public TranslateResponse translate(String text, String targetLanguage, String requestId) {
        TranslateResponse response = geminiClient.explain(text, targetLanguage, requestId);
        validate(response);
        return response;
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
