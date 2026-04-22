package com.incident.copilot.core.analysis;

/**
 * Framework-free seam between core analysis logic and the concrete LLM transport.
 * The core module depends on this interface; the application module provides
 * the implementation (e.g. an OpenAI HTTP client).
 */
public interface LlmClient {

    /**
     * Sends the given system and user prompts to the LLM and returns the raw
     * assistant response as a string.
     */
    String chat(String systemPrompt, String userMessage);
}
