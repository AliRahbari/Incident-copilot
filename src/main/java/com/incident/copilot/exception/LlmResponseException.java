package com.incident.copilot.exception;

/**
 * Thrown when the LLM returns content that cannot be parsed into the expected structured format.
 */
public class LlmResponseException extends RuntimeException {

    public LlmResponseException(String message) {
        super(message);
    }

    public LlmResponseException(String message, Throwable cause) {
        super(message, cause);
    }
}
