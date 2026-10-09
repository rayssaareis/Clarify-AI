package com.bureaucracytranslator.client;

import com.bureaucracytranslator.config.GeminiProperties;
import com.bureaucracytranslator.dto.SimplificationLevel;
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
 * The system instruction is the shared base rules plus the instructions of the
 * selected {@link SimplificationLevel}; nothing else about the flow changes.
 *
 * Never logs: the document text, the full prompt, or the full Gemini response.
 * Only requestId, duration and error type are logged.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";

    /**
     * Rules shared by every simplification level. Accuracy rules live here on
     * purpose: no level may relax them.
     */
    private static final String BASE_PROMPT = """
            You are a plain-language explainer for bureaucratic and legal documents.

            Rules:
            1. Only explain what is explicitly stated in the provided text. Never invent clauses, deadlines, amounts, fees, rights, obligations, consequences, or facts.
            2. Preserve relevant names, dates, amounts, conditions, and exceptions exactly as the document states them. Never round, alter, or merge them.
            3. Keep what the document says apart from what you add. Present your own explanations of terms or general implications as general information, not as something the document states.
            4. If a detail cannot be determined from the document, say so clearly instead of guessing.
            5. You explain documents; you do not give professional legal advice. Do not tell the person what they are legally entitled or obliged to do beyond what the document says. When a decision has legal or financial weight, suggest confirming with the issuing organization or a qualified professional.
            6. Never omit a critical warning, deadline, required action, or consequence, whatever the simplification level.
            7. Keep next steps grounded in the document: each step must follow from something the document states or asks for.
            8. Separate the result into:
               (a) a plain-language explanation
               (b) numbered concrete next steps
            9. targetLanguage determines the output language, including any section titles.
            10. If the input is unclear, garbled, too short, or insufficient to understand the document, say so instead of guessing.
            11. The simplification level below changes the depth, vocabulary, context, and length of the explanation. It never changes the facts of the source document.
            """;

    private static final String QUICK_SIMPLE_PROMPT = """
            Simplification level: QUICK_SIMPLE ("Quick & Simple"). The essentials, in plain language.
            - Use very common words and short sentences. Replace jargon with everyday words, and explain only the terms the person needs in order to understand and act.
            - Focus on the main meaning of the document and lead with its most important point.
            - Highlight the most important information (who, what, amounts, dates, deadlines) and the immediate next steps.
            - Keep the explanation concise and easy to scan: a few brief paragraphs at most, with no background or secondary detail.
            - Give few next steps (typically 2 to 4). Each one is a short action that starts with a verb, ordered by urgency.
            - Short never means incomplete: every deadline, required action, warning, or consequence the document states must still appear, stated briefly.
            """;

    private static final String CLEAR_DETAILED_PROMPT = """
            Simplification level: CLEAR_DETAILED ("Clear & Detailed"). A clear explanation with the context the person needs.
            - Explain the document in accessible, everyday language with a natural, readable flow.
            - Clarify important terms, requirements, deadlines, and conditions when they are present.
            - Explain why a point matters whenever the document supports that explanation.
            - Balance readability with useful detail. Include a secondary detail only when it affects what the person must do or understand.
            - Give practical, organized next steps in a logical order (typically 3 to 6), including who, when, or what to bring when the document says so.
            """;

    private static final String IN_DEPTH_PROMPT = """
            Simplification level: IN_DEPTH ("In-Depth Explanation"). More context, terminology, and details.
            - Give a comprehensive explanation that covers the parts of the document, not only its headline.
            - Explain relevant official or technical terminology in plain language. Keep the original term so the person can recognize it in the document, then say what it means.
            - Clarify how requirements, conditions, deadlines, and consequences relate to each other, whenever the document supports it.
            - Preserve important nuances and exceptions, including conditions that change what applies.
            - Organize the explanation into readable sections separated by blank lines. Start each section with a short plain-text title on its own line. Do not use Markdown symbols, because the text is displayed as plain text. Do not repeat information across sections.
            - Give complete, ordered next steps, each grounded in the document. Mention any condition or exception that affects a step.
            """;

    private final RestClient restClient;
    private final GeminiProperties properties;
    private final ObjectMapper objectMapper;

    public GeminiClient(GeminiProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.restClient = RestClient.builder().baseUrl(BASE_URL).build();
    }

    public TranslateResponse explain(String text, String targetLanguage, SimplificationLevel level, String requestId) {
        String userContent = buildUserContent(text, targetLanguage);
        Map<String, Object> requestBody = buildRequestBody(userContent, buildSystemPrompt(level));

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

    /** Base rules plus the instructions for the selected level (default level when null). */
    private String buildSystemPrompt(SimplificationLevel level) {
        SimplificationLevel effective = level == null ? SimplificationLevel.DEFAULT : level;
        String levelInstructions = switch (effective) {
            case QUICK_SIMPLE -> QUICK_SIMPLE_PROMPT;
            case CLEAR_DETAILED -> CLEAR_DETAILED_PROMPT;
            case IN_DEPTH -> IN_DEPTH_PROMPT;
        };
        return BASE_PROMPT + "\n" + levelInstructions;
    }

    private String buildUserContent(String text, String targetLanguage) {
        String languageInstruction = "en".equals(targetLanguage)
                ? "Respond in English."
                : "Respond in the same language as the input document below.";
        return languageInstruction + "\n\nDocument:\n" + text;
    }

    private Map<String, Object> buildRequestBody(String userContent, String systemPrompt) {
        Map<String, Object> systemInstruction = Map.of(
                "parts", List.of(Map.of("text", systemPrompt))
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
