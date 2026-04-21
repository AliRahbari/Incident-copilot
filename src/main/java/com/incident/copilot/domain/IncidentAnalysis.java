package com.incident.copilot.domain;

import java.util.List;

/**
 * Aggregate result of analyzing an {@link IncidentInput}. This is the core
 * value object returned by the analyzer; it is what any embedder of the library
 * (REST API, CLI, Slack bot, Jira integration) will receive.
 *
 * <p>All collection fields are defensively copied on construction so instances
 * are safe to share.
 */
public record IncidentAnalysis(
        String summary,
        IncidentSeverity severity,
        IncidentCategory category,
        List<IncidentObservation> observations,
        List<PossibleCause> possibleCauses,
        List<RecommendedAction> recommendedActions
) {

    public IncidentAnalysis {
        severity = severity == null ? IncidentSeverity.UNKNOWN : severity;
        category = category == null ? IncidentCategory.UNKNOWN : category;
        observations = observations == null ? List.of() : List.copyOf(observations);
        possibleCauses = possibleCauses == null ? List.of() : List.copyOf(possibleCauses);
        recommendedActions = recommendedActions == null ? List.of() : List.copyOf(recommendedActions);
    }
}
