package com.bureaucracytranslator.client;

import com.bureaucracytranslator.config.GeminiProperties;
import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Talks to the Gemini generateContent REST endpoint.
 * Requests structured JSON output (responseMimeType + responseSchema) so the
 * model's answer already matches the shape of {@link TranslateResponse}.
 *
 * Never logs: the document text, the full prompt, or the full Gemini response.
 * Only requestId, duration and error type are logged.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    private static final String SYSTEM_PROMPT = """
            You are a plain-language explainer for bureaucratic and legal documents.

            Rules:
            1. Only explain what is explicitly stated in the provided text. Never invent clauses, deadlines, amounts, obligations, or facts.
            2. Use short, simple sentences and avoid unnecessary legal jargon.
            3. Separate the result into:
               (a) a plain-language explanation
               (b) numbered concrete next steps
            4. targetLanguage determines the output language.
            5. If the input is unclear, garbled, too short, or insufficient to understand the document, say so instead of guessing.
            """;

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public TranslateResponse explain(String text, String targetLanguage, String requestId) {
        String userContent = buildUserContent(text, targetLanguage);
        Map<String, Object> requestBody = buildRequestBody(userContent);

        long startedAt = System.currentTimeMillis();
        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .uri("/v1beta/models/{model}:generateContent", properties.model())
                    .header("x-goog-api-key", properties.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientException ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn("Gemini API call failed requestId={} durationMs={} errorType={}",
                    requestId, durationMs, ex.getClass().getSimpleName());
            throw new GeminiServiceException("Gemini API call failed", ex);
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("Gemini API call succeeded requestId={} durationMs={}", requestId, durationMs);

        String innerJson = extractInnerJson(rawResponse, requestId);
        return parseTranslateResponse(innerJson, requestId);
    }

    private String buildUserContent(String text, String targetLanguage) {
        String languageInstruction = "en".equals(targetLanguage)
                ? "Respond in English."
                : "Respond in the same language as the input document below.";
        return languageInstruction + "\n\nDocument:\n" + text;
    }

    private Map<String, Object> buildRequestBody(String userContent) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", SYSTEM_PROMPT))
        );

        Map<String, Object> userPart = Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", userContent))
        );

        Map<String, Object> responseSchema = new LinkedHashMap<>();
        responseSchema.put("type", "OBJECT");
        responseSchema.put("properties", Map.of(
                "explanation", Map.of("type", "STRING"),
                "nextSteps", Map.of(
                        "type", "ARRAY",
                        "items", Map.of("type", "STRING")
                )
        ));
        responseSchema.put("required", List.of("explanation", "nextSteps"));

        Map<String, Object> generationConfig = Map.of(
                "responseMimeType", "application/json",
                "responseSchema", responseSchema
        );

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("systemInstruction", systemInstruction);
        body.put("contents", List.of(userPart));
        body.put("generationConfig", generationConfig);
        return body;
    }

    private String extractInnerJson(String rawResponse, String requestId) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);
            JsonNode textNode = root.path("candidates").path(0)
                    .path("content").path("parts").path(0).path("text");

            if (textNode.isMissingNode() || !textNode.isTextual() || textNode.asText().isBlank()) {
                log.warn("Gemini response missing expected content requestId={}", requestId);
                throw new GeminiServiceException("Gemini response missing expected content");
            }
            return textNode.asText();
        } catch (GeminiServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse Gemini envelope requestId={} errorType={}",
                    requestId, ex.getClass().getSimpleName());
            throw new GeminiServiceException("Failed to parse Gemini response", ex);
        }
    }

    private TranslateResponse parseTranslateResponse(String innerJson, String requestId) {
        try {
            return objectMapper.readValue(innerJson, TranslateResponse.class);
        } catch (Exception ex) {
            log.warn("Failed to parse structured Gemini output requestId={} errorType={}",
                    requestId, ex.getClass().getSimpleName());
            throw new GeminiServiceException("Failed to parse structured Gemini output", ex);
        }
    }
}
