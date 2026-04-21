package com.incident.copilot.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.client.OpenAiClient;
import com.incident.copilot.domain.IncidentAnalysis;
import com.incident.copilot.domain.IncidentCategory;
import com.incident.copilot.domain.IncidentInput;
import com.incident.copilot.domain.IncidentObservation;
import com.incident.copilot.domain.IncidentSeverity;
import com.incident.copilot.domain.PossibleCause;
import com.incident.copilot.domain.RecommendedAction;
import com.incident.copilot.exception.LlmResponseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class IncidentAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(IncidentAnalysisService.class);

    private static final String SYSTEM_PROMPT = """
            You are an expert Site Reliability Engineer and JVM performance engineer \
            performing incident triage.
            Analyze the application logs, error messages, or stack traces provided by the user.

            ## Analysis process
            1. OBSERVE: Identify concrete facts visible in the input — error messages, \
            exception types, class names, line numbers, HTTP status codes, timestamps, \
            repeated patterns, thread names, lock states, GC log entries.

            2. CONTEXTUALIZE: When the input contains enough clues, determine:
               - Thread/execution context: Is this a UI thread (e.g. AWT-EventQueue, \
            JavaFX Application Thread), event-dispatch thread, request-handler thread \
            (e.g. http-nio, catalina-exec), worker/background thread, GC thread, or \
            finalizer thread? Use thread names, stack prefixes, and framework-specific \
            patterns as evidence.
               - Failure stage: Did this occur during startup/class-loading/initialization \
            (look for static initializers, <clinit>, @PostConstruct, servlet init, \
            Spring context refresh), request handling, shutdown, or background processing?
               - Resource pattern: Is this CPU-bound (tight loops, recursive calls, \
            compilation), IO-bound (socket/file reads, JDBC calls, HTTP clients), \
            GC-related (GC overhead, allocation pressure, stop-the-world pauses, \
            promotion failure), lock contention (synchronized, ReentrantLock, \
            Object.wait, LockSupport.park), or thread starvation (exhausted pools, \
            queued tasks, all threads BLOCKED/WAITING)?
               Skip any dimension that has no supporting evidence in the input.

            3. DIAGNOSE: Based on observations and execution context, infer root causes.
               - Rank by likelihood. Assign a confidence level to each.
               - Distinguish primary clues (directly causal) from secondary clues \
            (corroborating or circumstantial) in your evidence.
               - When multiple explanations are plausible, state the ambiguity explicitly \
            and explain why one is preferred.
               - Name the specific failure mechanism, not just the symptom. For example: \
            "class-loading lock contention during lazy singleton initialization on the \
            request thread" instead of "slow initialization"; "stop-the-world Full GC \
            pause triggered by promotion failure" instead of "GC issue".
               - Avoid generic causes like "inefficient code execution" or "performance \
            problem" — always name the mechanism.

            4. RECOMMEND: Suggest debugging steps tied to the specific execution path, \
            stack frames, and runtime context found in the input.

            ## Rules
            - Be concise. No filler, no preamble.
            - Every claim must be traceable to something in the input. If you cite a \
            class name, line number, or error code, it must appear in the input verbatim.
            - Do NOT invent package names, class names, error codes, or stack frames \
            that are not present in the input.
            - If the input is insufficient for a confident diagnosis, say so explicitly \
            in the summary and return fewer causes.
            - Do not list generic causes (e.g. "network issue", "configuration error", \
            "inefficient code") unless the input specifically supports them with \
            concrete evidence.
            - For stack traces: read the full call chain. Identify which frame is the \
            likely blocking or failing point and explain why frames above and below it \
            matter. Do not stop at the top frame.
            - For thread dumps: identify thread states (BLOCKED, WAITING, \
            TIMED_WAITING, RUNNABLE) and correlate lock holders with lock waiters \
            when visible.
            - For GC logs: distinguish minor/major/full GC, note pause durations, and \
            assess whether GC is a primary cause or a symptom of allocation pressure.
            - Tailor your language to a developer audience.

            ## Response format
            Respond with valid JSON only — no markdown fences, no extra text.
            Use exactly this schema and these field names:
            {
              "summary": "<1-2 sentences: what is failing, the execution context \
            if identifiable, and your overall confidence>",
              "observations": [
                "<concrete fact directly visible in the input>"
              ],
              "possibleCauses": [
                {
                  "cause": "<one sentence root cause naming the specific mechanism>",
                  "confidence": "high|medium|low",
                  "evidence": ["<quote or reference from the input, \
            prefixed [primary] or [secondary] when the distinction matters>"]
                }
              ],
              "nextSteps": [
                "<specific debugging action referencing code/log locations \
            and execution context from input>"
              ]
            }

            ## Field guidelines
            - summary: What is broken, on which thread/context if identifiable, at \
            what stage if identifiable, and the impact. Use "likely" or "possibly" \
            when uncertain. If the input is too vague for diagnosis, state that.
            - observations: 2-8 concrete, directly observed facts. Quote error messages \
            or reference specific stack frames. Include thread names, lock states, and \
            GC metrics when present in the input. These are facts, not interpretations.
            - possibleCauses: At most 4, ranked most likely first.
              - cause: Name the specific failure mechanism, not just the symptom. \
            Include thread context and failure stage when identifiable.
              - confidence: "high" = directly stated or visible in the input; \
            "medium" = strongly implied by the evidence; "low" = plausible but uncertain.
              - evidence: 1-3 strings quoting or referencing specific parts of the input. \
            Prefix with [primary] for directly causal clues or [secondary] for \
            corroborating clues when the distinction is meaningful.
            - nextSteps: At most 5 concrete actions. Reference specific classes, methods, \
            config files, or log lines from the input. Tie recommendations to the \
            identified execution context and resource pattern — e.g. "attach \
            async-profiler in lock mode to capture the monitor holder at ClassName.method" \
            rather than "investigate the issue".
            """;

    private final OpenAiClient openAiClient;
    private final ObjectMapper objectMapper;

    public IncidentAnalysisService(OpenAiClient openAiClient, ObjectMapper objectMapper) {
        this.openAiClient = openAiClient;
        this.objectMapper = objectMapper;
    }

    /**
     * Analyze an incident and return a framework-agnostic {@link IncidentAnalysis}.
     * Severity and category are emitted as {@code UNKNOWN} in Phase 1 — the
     * concepts exist on the domain but the LLM prompt does not yet populate them.
     */
    public IncidentAnalysis analyze(IncidentInput input) {
        log.info("Analyzing input ({} chars)", input.content().length());

        String rawResponse = openAiClient.chat(SYSTEM_PROMPT, input.content());
        String json = stripMarkdownFences(rawResponse);

        LlmAnalysisResponse parsed;
        try {
            parsed = objectMapper.readValue(json, LlmAnalysisResponse.class);
        } catch (Exception e) {
            log.error("LLM returned unparseable response: {}", rawResponse, e);
            throw new LlmResponseException("Failed to parse LLM response into structured format", e);
        }

        return toDomain(parsed);
    }

    private static IncidentAnalysis toDomain(LlmAnalysisResponse llm) {
        List<IncidentObservation> observations = llm.observations() == null
                ? List.of()
                : llm.observations().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(IncidentObservation::new)
                        .toList();

        List<PossibleCause> causes = llm.possibleCauses() == null
                ? List.of()
                : llm.possibleCauses().stream()
                        .map(c -> new PossibleCause(c.cause(), c.confidence(), c.evidence()))
                        .toList();

        List<RecommendedAction> actions = llm.nextSteps() == null
                ? List.of()
                : llm.nextSteps().stream()
                        .filter(s -> s != null && !s.isBlank())
                        .map(RecommendedAction::new)
                        .toList();

        return new IncidentAnalysis(
                llm.summary(),
                IncidentSeverity.UNKNOWN,
                IncidentCategory.UNKNOWN,
                observations,
                causes,
                actions
        );
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

    /**
     * Shape of the LLM's JSON output. Kept package-private and separate from the
     * public {@code AnalyzeResponse} DTO so the wire contract and the prompt
     * contract can evolve independently.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    record LlmAnalysisResponse(
            String summary,
            List<String> observations,
            List<LlmPossibleCause> possibleCauses,
            List<String> nextSteps
    ) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        record LlmPossibleCause(String cause, String confidence, List<String> evidence) {}
    }
}
