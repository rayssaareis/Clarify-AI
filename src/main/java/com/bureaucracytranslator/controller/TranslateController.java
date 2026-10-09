package com.bureaucracytranslator.controller;

import com.bureaucracytranslator.dto.SimplificationLevel;
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
import org.springframework.web.multipart.MultipartFile;

import java.util.Set;
import java.util.UUID;

/**
 * Consumes multipart/form-data per the final API contract: exactly one of
 * "text" or "image", plus "targetLanguage" and an optional "simplificationLevel"
 * (defaults to CLEAR_DETAILED when omitted or blank).
 */
@RestController
@RequestMapping("/api")
public class TranslateController {

    private static final Logger log = LoggerFactory.getLogger(TranslateController.class);
    private static final Set<String> ALLOWED_LANGUAGES = Set.of("original", "en");
    private static final Set<String> SUPPORTED_IMAGE_TYPES = Set.of("image/jpeg", "image/jpg", "image/png");
    private static final long MAX_IMAGE_SIZE_BYTES = 1_048_576L; // 1 MB

    private final TranslateService translateService;

    public TranslateController(TranslateService translateService) {
        this.translateService = translateService;
    }

    @PostMapping(value = "/translate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TranslateResponse> translate(
            @RequestParam(required = false) String text,
            @RequestParam(required = false) MultipartFile image,
            @RequestParam(defaultValue = "original") String targetLanguage,
            @RequestParam(required = false) String simplificationLevel) {

        String requestId = UUID.randomUUID().toString();
        log.info("Received /api/translate request requestId={}", requestId);

        boolean hasText = text != null && !text.isBlank();
        boolean hasImage = image != null && !image.isEmpty();

        if (hasText && hasImage) {
            throw new InvalidRequestException("Provide either 'text' or 'image', not both.");
        }
        if (!hasText && !hasImage) {
            throw new InvalidRequestException("Either 'text' or 'image' is required.");
        }
        if (!ALLOWED_LANGUAGES.contains(targetLanguage)) {
            throw new InvalidRequestException("Field 'targetLanguage' must be 'original' or 'en'.");
        }
        SimplificationLevel level = resolveLevel(simplificationLevel);

        TranslateResponse response;
        if (hasText) {
            if (text.length() > TranslateService.MAX_TEXT_LENGTH) {
                throw new InvalidRequestException(
                        "Field 'text' is too long (max " + TranslateService.MAX_TEXT_LENGTH + " characters).");
            }
            response = translateService.translate(text, targetLanguage, level, requestId);
        } else {
            validateImage(image);
            response = translateService.translateImage(image, targetLanguage, level, requestId);
        }

        return ResponseEntity.ok(response);
    }

    private SimplificationLevel resolveLevel(String rawLevel) {
        if (rawLevel == null || rawLevel.isBlank()) {
            return SimplificationLevel.DEFAULT;
        }
        return SimplificationLevel.fromValue(rawLevel)
                .orElseThrow(() -> new InvalidRequestException(
                        "Field 'simplificationLevel' must be one of: " + SimplificationLevel.acceptedValues() + "."));
    }

    private void validateImage(MultipartFile image) {
        String contentType = image.getContentType();
        if (contentType == null || !SUPPORTED_IMAGE_TYPES.contains(contentType.toLowerCase())) {
            throw new InvalidRequestException("Unsupported image type. Only JPEG and PNG are accepted.");
        }
        if (image.getSize() > MAX_IMAGE_SIZE_BYTES) {
            throw new InvalidRequestException("Image is too large (max 1 MB).");
        }
    }
}
