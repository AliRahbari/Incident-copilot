package com.incident.copilot.dto;

import com.incident.copilot.domain.IncidentAnalysis;
import com.incident.copilot.domain.IncidentObservation;
import com.incident.copilot.domain.RecommendedAction;

/**
 * Converts the framework-agnostic {@link IncidentAnalysis} domain type into the
 * HTTP wire format {@link AnalyzeResponse}. Lives in the {@code dto} package
 * because it is a transport concern — when the core library is extracted, this
 * class stays on the API/starter side.
 */
public final class IncidentAnalysisMapper {

    private IncidentAnalysisMapper() {}

    public static AnalyzeResponse toResponse(IncidentAnalysis analysis) {
        return new AnalyzeResponse(
                analysis.summary(),
                analysis.observations().stream()
                        .map(IncidentObservation::description)
                        .toList(),
                analysis.possibleCauses().stream()
                        .map(c -> new PossibleCause(c.cause(), c.confidence(), c.evidence()))
                        .toList(),
                analysis.recommendedActions().stream()
                        .map(RecommendedAction::description)
                        .toList()
        );
    }
}
