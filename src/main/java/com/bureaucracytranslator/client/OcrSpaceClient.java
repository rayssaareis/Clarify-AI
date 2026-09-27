package com.bureaucracytranslator.client;

import com.bureaucracytranslator.config.OcrSpaceProperties;
import com.bureaucracytranslator.exception.OcrServiceException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Talks to the OCR.space REST API to extract text from an uploaded image.
 *
 * Never logs: image bytes, extracted text, or the full OCR.space response.
 * Only requestId, duration and error type are logged.
 */
@Component
public class OcrSpaceClient {

    private static final Logger log = LoggerFactory.getLogger(OcrSpaceClient.class);

    private static final String BASE_URL = "https://api.ocr.space";
    private static final String OCR_LANGUAGE = "por";

    private final RestClient restClient;
    private final OcrSpaceProperties properties;
    private final ObjectMapper objectMapper;

    public OcrSpaceClient(RestClient.Builder restClientBuilder,
                          OcrSpaceProperties properties,
                          ObjectMapper objectMapper) {
        this.restClient = restClientBuilder.baseUrl(BASE_URL).build();
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String extractText(MultipartFile image, String requestId) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("apikey", properties.apiKey());
        body.add("language", OCR_LANGUAGE);
        body.add("isOverlayRequired", "false");
        body.add("file", toFilePart(image));

        long startedAt = System.currentTimeMillis();
        String rawResponse;
        try {
            rawResponse = restClient.post()
                    .uri("/parse/image")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (RestClientResponseException ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn("OCR.space call failed requestId={} durationMs={} status={} body={}",
                    requestId, durationMs, ex.getStatusCode(), ex.getResponseBodyAsString());
            throw new OcrServiceException("OCR.space call failed", ex);
        } catch (RestClientException ex) {
            long durationMs = System.currentTimeMillis() - startedAt;
            log.warn("OCR.space call failed requestId={} durationMs={} errorType={}",
                    requestId, durationMs, ex.getClass().getSimpleName());
            throw new OcrServiceException("OCR.space call failed", ex);
        }

        long durationMs = System.currentTimeMillis() - startedAt;
        log.info("OCR.space call succeeded requestId={} durationMs={}", requestId, durationMs);

        return extractParsedText(rawResponse, requestId);
    }

    private ByteArrayResource toResource(MultipartFile file) {
        try {
            byte[] bytes = file.getBytes();
            return new ByteArrayResource(bytes) {
                @Override
                public String getFilename() {
                    String original = file.getOriginalFilename();
                    return (original != null && !original.isBlank()) ? original : "document";
                }
            };
        } catch (IOException ex) {
            throw new OcrServiceException("Failed to read uploaded image", ex);
        }
    }

    private HttpEntity<Resource> toFilePart(MultipartFile file) {
        HttpHeaders fileHeaders = new HttpHeaders();
        fileHeaders.setContentType(MediaType.parseMediaType(file.getContentType()));
        return new HttpEntity<>(toResource(file), fileHeaders);
    }

    private String extractParsedText(String rawResponse, String requestId) {
        try {
            JsonNode root = objectMapper.readTree(rawResponse);

            boolean erroredOnProcessing = root.path("IsErroredOnProcessing").asBoolean(false);
            if (erroredOnProcessing) {
                log.warn("OCR.space reported a processing error requestId={}", requestId);
                throw new OcrServiceException("OCR.space reported a processing error");
            }

            JsonNode parsedTextNode = root.path("ParsedResults").path(0).path("ParsedText");
            if (parsedTextNode.isMissingNode() || !parsedTextNode.isTextual()) {
                log.warn("OCR.space response missing expected content requestId={}", requestId);
                throw new OcrServiceException("OCR.space response missing expected content");
            }

            return parsedTextNode.asText();
        } catch (OcrServiceException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Failed to parse OCR.space response requestId={} errorType={}",
                    requestId, ex.getClass().getSimpleName());
            throw new OcrServiceException("Failed to parse OCR.space response", ex);
        }
    }
}