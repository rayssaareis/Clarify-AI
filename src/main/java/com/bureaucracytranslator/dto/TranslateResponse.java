package com.bureaucracytranslator.dto;

import java.util.List;

public record TranslateResponse(String explanation, List<String> nextSteps) {
}
