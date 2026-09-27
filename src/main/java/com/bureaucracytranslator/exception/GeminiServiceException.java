package com.bureaucracytranslator.exception;

public class GeminiServiceException extends RuntimeException {

    public GeminiServiceException(String message) {
        super(message);
    }

    public GeminiServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
