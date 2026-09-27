package com.bureaucracytranslator.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Reads Gemini configuration from environment variables.
 * GEMINI_API_KEY has no default: if it is not set, Spring fails to start
 * the application with a clear placeholder-resolution error, which is the
 * desired "required" behavior.
 */
@Component
public class GeminiProperties {

    private final String apiKey;
    private final String model;

    public GeminiProperties(
            @Value("${GEMINI_API_KEY}") String apiKey,
            @Value("${GEMINI_MODEL:gemini-3.5-flash-lite}") String model) {
        this.apiKey = apiKey;
        this.model = model;
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }
}
