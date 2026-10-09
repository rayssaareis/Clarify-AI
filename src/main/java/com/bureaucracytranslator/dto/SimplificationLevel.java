package com.bureaucracytranslator.dto;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * How deep the explanation should go. The wire value is the enum name
 * (e.g. "CLEAR_DETAILED"); the matching AI instructions live in GeminiClient.
 */
public enum SimplificationLevel {

    QUICK_SIMPLE,
    CLEAR_DETAILED,
    IN_DEPTH;

    /** Used when the request does not specify a level (keeps older clients working). */
    public static final SimplificationLevel DEFAULT = CLEAR_DETAILED;

    /** Case-insensitive lookup; empty when the value is not an accepted level. */
    public static Optional<SimplificationLevel> fromValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(level -> level.name().equalsIgnoreCase(normalized))
                .findFirst();
    }

    /** "QUICK_SIMPLE, CLEAR_DETAILED, IN_DEPTH", for error messages. */
    public static String acceptedValues() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
