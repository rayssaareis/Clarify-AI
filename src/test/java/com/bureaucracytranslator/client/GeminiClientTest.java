package com.bureaucracytranslator.client;

import com.bureaucracytranslator.config.GeminiProperties;
import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.GeminiServiceException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Tests the real internal logic of GeminiClient (request body, structured-output
 * schema, envelope extraction, inner JSON parsing, error branches) with no
 * network access and no environment variables.
 *
 * GeminiClient builds its own RestClient inside the constructor (no injection
 * point), so the private "restClient" field is replaced via reflection with one
 * bound to a MockRestServiceServer. This keeps production code untouched, at the
 * cost of coupling this test to that field name.
 */
class GeminiClientTest {

    private static final String BASE_URL = "https://generativelanguage.googleapis.com";
    private static final String MODEL = "test-model";
    private static final String API_KEY = "test-gemini-key";
    private static final String GEMINI_URL = BASE_URL + "/v1beta/models/" + MODEL + ":generateContent";

    private final ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();

    private MockRestServiceServer server;
    private GeminiClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(new GeminiProperties(API_KEY, MODEL), objectMapper);
        ReflectionTestUtils.setField(client, "restClient", builder.build());
    }

    // ---------- helpers ----------

    /** Builds the Gemini generateContent envelope around the model's text output. */
    private String envelope(String modelText) throws JsonProcessingException {
        return objectMapper.writeValueAsString(Map.of(
                "candidates", List.of(Map.of(
                        "content", Map.of(
                                "parts", List.of(Map.of("text", modelText)))))));
    }

    /** Builds the inner structured JSON that Gemini returns as text. */
    private String structured(String explanation, List<String> nextSteps) throws JsonProcessingException {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("explanation", explanation);
        inner.put("nextSteps", nextSteps);
        return objectMapper.writeValueAsString(inner);
    }

    // ---------- valid responses ----------

    @Test
    void explain_validStructuredResponse_returnsParsedTranslateResponse() throws Exception {
        String body = envelope(structured("Voce deve comparecer a audiencia.",
                List.of("Ir a audiencia no dia 15/10", "Levar documento com foto")));
        server.expect(requestTo(GEMINI_URL))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

        TranslateResponse result = client.explain("Comparecer no dia 15/10.", "original", "req-1");

        assertThat(result.explanation()).isEqualTo("Voce deve comparecer a audiencia.");
        assertThat(result.nextSteps())
                .containsExactly("Ir a audiencia no dia 15/10", "Levar documento com foto");
        server.verify();
    }

    @Test
    void explain_usesConfiguredModelInUrl() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        MockRestServiceServer otherServer = MockRestServiceServer.bindTo(builder).build();
        GeminiClient otherClient = new GeminiClient(new GeminiProperties(API_KEY, "another-model"), objectMapper);
        ReflectionTestUtils.setField(otherClient, "restClient", builder.build());

        otherServer.expect(requestTo(BASE_URL + "/v1beta/models/another-model:generateContent"))
                .andRespond(withSuccess(
                        envelope(structured("ok", List.of("step"))), MediaType.APPLICATION_JSON));

        otherClient.explain("text", "original", "req-2");

        otherServer.verify();
    }

    // ---------- request shape ----------

    @Test
    void explain_sendsStructuredOutputRequestWithApiKeyOnlyInHeader() throws Exception {
        server.expect(requestTo(GEMINI_URL))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", API_KEY))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.generationConfig.responseMimeType").value("application/json"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.type").value("OBJECT"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.explanation.type").value("STRING"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.nextSteps.type").value("ARRAY"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.properties.nextSteps.items.type").value("STRING"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.required[0]").value("explanation"))
                .andExpect(jsonPath("$.generationConfig.responseSchema.required[1]").value("nextSteps"))
                .andExpect(jsonPath("$.systemInstruction.parts[0].text").value(containsString("plain-language explainer")))
                .andExpect(jsonPath("$.contents[0].role").value("user"))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(containsString("Unique document text 123")))
                .andExpect(content().string(not(containsString(API_KEY))))
                .andRespond(withSuccess(
                        envelope(structured("ok", List.of("step"))), MediaType.APPLICATION_JSON));

        client.explain("Unique document text 123", "original", "req-3");

        server.verify();
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "original | Respond in the same language as the input document below.",
            "en       | Respond in English."
    })
    void explain_targetLanguageControlsTheLanguageInstruction(String targetLanguage, String expectedInstruction)
            throws Exception {
        server.expect(requestTo(GEMINI_URL))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value(containsString(expectedInstruction)))
                .andRespond(withSuccess(
                        envelope(structured("ok", List.of("step"))), MediaType.APPLICATION_JSON));

        client.explain("some document", targetLanguage, "req-4");

        server.verify();
    }

    // ---------- extractInnerJson(): unusable envelopes ----------

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"candidates\":[]}",
            "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}",
            "{\"candidates\":[{\"finishReason\":\"SAFETY\"}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[]}}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"   \"}]}}]}",
            "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":123}]}}]}"
    })
    void explain_whenEnvelopeHasNoUsableText_throwsGeminiServiceException(String responseBody) {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.explain("text", "original", "req-5"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("missing expected content");
    }

    @Test
    void explain_whenEnvelopeIsNotJson_throwsGeminiServiceException() {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withSuccess("<html>unexpected</html>", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.explain("text", "original", "req-6"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Failed to parse Gemini response");
    }

    // ---------- parseTranslateResponse(): inner structured JSON ----------

    @Test
    void explain_whenInnerTextIsNotJson_throwsGeminiServiceException() throws Exception {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withSuccess(envelope("this is plain text, not JSON"), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.explain("text", "original", "req-7"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Failed to parse structured Gemini output");
    }

    @Test
    void explain_whenInnerJsonIsTruncated_throwsGeminiServiceException() throws Exception {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withSuccess(
                        envelope("{\"explanation\":\"cut off midway\",\"nextSteps\":[\"a\""),
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.explain("text", "original", "req-8"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("Failed to parse structured Gemini output");
    }

    /**
     * Documents the division of responsibility: the client only parses. Checking
     * that explanation/nextSteps are present and non-blank is TranslateService's job,
     * so a structurally parseable but incomplete payload is returned as-is here.
     */
    @Test
    void explain_whenInnerJsonLacksNextSteps_isReturnedAsIs_validationBelongsToService() throws Exception {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withSuccess(envelope("{\"explanation\":\"only this\"}"), MediaType.APPLICATION_JSON));

        TranslateResponse result = client.explain("text", "original", "req-9");

        assertThat(result.explanation()).isEqualTo("only this");
        assertThat(result.nextSteps()).isNull();
    }

    // ---------- HTTP / network failures ----------

    /** Includes 404, the real failure seen when gemini-2.5-flash-lite was retired for new users. */
    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {
            "BAD_REQUEST", "UNAUTHORIZED", "FORBIDDEN", "NOT_FOUND",
            "TOO_MANY_REQUESTS", "INTERNAL_SERVER_ERROR", "SERVICE_UNAVAILABLE"
    })
    void explain_whenGeminiReturnsHttpError_throwsGeminiServiceExceptionWithResponseCause(HttpStatus status) {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withStatus(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"message\":\"upstream error\"}}"));

        assertThatThrownBy(() -> client.explain("text", "original", "req-10"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("call failed")
                .hasCauseInstanceOf(RestClientResponseException.class);
    }

    @Test
    void explain_whenNetworkFails_throwsGeminiServiceExceptionWithResourceAccessCause() {
        server.expect(requestTo(GEMINI_URL))
                .andRespond(withException(new IOException("connection reset")));

        assertThatThrownBy(() -> client.explain("text", "original", "req-11"))
                .isInstanceOf(GeminiServiceException.class)
                .hasMessageContaining("call failed")
                .hasCauseInstanceOf(ResourceAccessException.class);
    }
}
