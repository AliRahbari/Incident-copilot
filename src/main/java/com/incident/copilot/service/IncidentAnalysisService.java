package com.incident.copilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.client.OpenAiClient;
import com.incident.copilot.dto.AnalyzeResponse;
import com.incident.copilot.exception.LlmResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class IncidentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert Site Reliability Engineer and backend developer.
            Your job is to analyze application logs, error messages, and stack traces
            provided by the user and produce a structured incident analysis.

            Rules:
            - Be concise and actionable. No filler text.
            - Only state root causes you can justify from the provided input.
              If the input is ambiguous, say so rather than guessing.
            - Do NOT hallucinate package names, class names, or error codes
              that are not present in the input.
            - Tailor your language to a developer audience.

            You MUST respond with valid JSON only — no markdown fences, no extra text.
            Use exactly this schema:
            {
              "summary": "<one or two sentence plain-English summary of the incident>",
              "possibleCauses": ["<cause 1>", "<cause 2>"],
              "nextSteps": ["<step 1>", "<step 2>"]
            }

            Guidelines for each field:
            - summary: Describe what is failing and the immediate impact.
            - possibleCauses: List the most likely root causes ranked by probability.
              Include at most 5 items.
            - nextSteps: Provide concrete debugging or remediation steps a developer
              can take right now. Include at most 5 items.
            """;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public IncidentAnalysisService(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    public AnalyzeResponse analyze(String input) {
        log.info("Analyzing input ({} chars)", input.length());

        String rawResponse = openAiClient.chat(SYSTEM_PROMPT, input);
        String json = stripMarkdownFences(rawResponse);

        try {
            return objectMapper.readValue(json, AnalyzeResponse.class);
        } catch (Exception e) {
            log.error("LLM returned unparseable response: {}", rawResponse, e);
            throw new LlmResponseException("Failed to parse LLM response into structured format", e);
        }
    }

    /**
     * Strips markdown code fences if the model wraps its JSON output.
     */
    static String stripMarkdownFences(String raw) {
        String stripped = raw.strip();
        if (stripped.startsWith("```")) {
            stripped = stripped.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return stripped;
    }
}
