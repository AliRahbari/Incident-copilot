package com.incident.copilot.domain;

import java.util.List;

/**
 * A ranked root-cause hypothesis produced by the analyzer.
 *
 * <p>{@code confidence} is kept as a free-form string for MVP ("high" / "medium" /
 * "low") to match what the LLM produces today. Promoting it to an enum is a future
 * refinement and not a Phase 1 concern.
 */
public record PossibleCause(
        String cause,
        String confidence,
        List<String> evidence
) {

    public PossibleCause {
        evidence = evidence == null ? List.of() : List.copyOf(evidence);
    }
}
