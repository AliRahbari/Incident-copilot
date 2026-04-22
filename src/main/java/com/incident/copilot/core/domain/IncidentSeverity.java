package com.incident.copilot.core.domain;

/**
 * Qualitative severity of an analyzed incident. {@link #UNKNOWN} is used when the
 * analyzer cannot confidently assign a severity from the input; downstream
 * consumers (routing, alerting) can treat it as a signal to fall back to their
 * own heuristics.
 */
public enum IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL,
    UNKNOWN
}
