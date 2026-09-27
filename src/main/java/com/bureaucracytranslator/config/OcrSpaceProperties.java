package com.bureaucracytranslator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads OCR.space configuration from environment variables.
 * OCR_SPACE_API_KEY has no default: if it is not set, Spring fails to start
 * the application with a clear placeholder-resolution error, same behavior
 * as GeminiProperties.
 */
@Component
public class OcrSpaceProperties {

    private final String apiKey;

    public OcrSpaceProperties(@Value("${OCR_SPACE_API_KEY}") String apiKey) {
        this.apiKey = apiKey;
    }

    public String apiKey() {
        return apiKey;
    }
}
