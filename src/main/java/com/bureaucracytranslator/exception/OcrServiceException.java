package com.bureaucracytranslator.exception;

public class OcrServiceException extends RuntimeException {

    public OcrServiceException(String message) {
        super(message);
    }

    public OcrServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
