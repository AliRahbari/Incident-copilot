package com.incident.copilot.core.exception;

/**
 * Thrown when the OpenAI API call fails or returns an unusable response structure.
 */
public class LlmClientException extends RuntimeException {

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
