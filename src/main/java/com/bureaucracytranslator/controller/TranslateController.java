package com.bureaucracytranslator.controller;

import com.bureaucracytranslator.dto.TranslateResponse;
import com.bureaucracytranslator.exception.InvalidRequestException;
import com.bureaucracytranslator.service.TranslateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;
import java.util.UUID;

/**
 * Consumes multipart/form-data to stay compatible with the final API contract
 * (text + image + targetLanguage). The "image" field is intentionally not
 * accepted yet — that is a later step.
 */
@RestController
@RequestMapping("/api")
public class TranslateController {

    private static final Logger log = LoggerFactory.getLogger(TranslateController.class);
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("original", "en");
    private static final int MAX_TEXT_LENGTH = 8000;

    private final TranslateService translateService;

    public TranslateController(TranslateService translateService) {
        this.translateService = translateService;
    }

    @PostMapping(value = "/translate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TranslateResponse> translate(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "original") String targetLanguage) {

        String requestId = UUID.randomUUID().toString();
        log.info("Received /api/translate request requestId={}", requestId);

        if (text == null || text.isBlank()) {
            throw new InvalidRequestException("Field 'text' is required.");
        }
        if (text.length() > MAX_TEXT_LENGTH) {
            throw new InvalidRequestException(
                    "Field 'text' is too long (max " + MAX_TEXT_LENGTH + " characters).");
        }
        if (!ALLOWED_LANGUAGES.contains(targetLanguage)) {
            throw new InvalidRequestException("Field 'targetLanguage' must be 'original' or 'en'.");
        }

        TranslateResponse response = translateService.translate(text, targetLanguage, requestId);
        return ResponseEntity.ok(response);
    }
}
