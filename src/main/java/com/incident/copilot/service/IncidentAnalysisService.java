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
            You are an expert Site Reliability Engineer performing incident triage.
            Analyze the application logs, error messages, or stack traces provided by the user.

            ## Analysis process
            1. OBSERVE: Identify concrete facts visible in the input — error messages, \
            exception types, class names, line numbers, HTTP status codes, timestamps, \
            repeated patterns.
            2. DIAGNOSE: Based only on those observations, infer possible root causes. \
            Rank by likelihood. Assign a confidence level to each.
            3. RECOMMEND: Suggest debugging steps tied to specific code locations, log \
            entries, or configuration mentioned in the input.

            ## Rules
            - Be concise. No filler, no preamble.
            - Every claim must be traceable to something in the input. If you cite a \
            class name, line number, or error code, it must appear in the input verbatim.
            - Do NOT invent package names, class names, error codes, or stack frames \
            that are not present in the input.
            - If the input is insufficient for a confident diagnosis, say so explicitly \
            in the summary and return fewer causes.
            - Do not list generic causes (e.g. "network issue", "configuration error") \
            unless the input specifically supports them with concrete evidence.
            - Tailor your language to a developer audience.

            ## Response format
            Respond with valid JSON only — no markdown fences, no extra text.
            Use exactly this schema and these field names:
            {
              "summary": "<1-2 sentences: what is failing and your overall confidence>",
              "observations": [
                "<concrete fact directly visible in the input>"
              ],
              "possibleCauses": [
                {
                  "cause": "<one sentence root cause>",
                  "confidence": "high|medium|low",
                  "evidence": ["<quote or reference from the input>"]
                }
              ],
              "nextSteps": [
                "<specific debugging action referencing code/log locations from input>"
              ]
            }

            ## Field guidelines
            - summary: What is broken and the impact. Use "likely" or "possibly" when \
            uncertain. If the input is too vague for diagnosis, state that.
            - observations: 2-8 concrete, directly observed facts. Quote error messages \
            or reference specific stack frames. These are facts, not interpretations.
            - possibleCauses: At most 4, ranked most likely first.
              - confidence: "high" = directly stated or visible in the input; \
            "medium" = strongly implied by the evidence; "low" = plausible but uncertain.
              - evidence: 1-3 strings quoting or referencing specific parts of the input.
            - nextSteps: At most 5 concrete actions. Reference specific classes, methods, \
            config files, or log lines from the input where possible.
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
