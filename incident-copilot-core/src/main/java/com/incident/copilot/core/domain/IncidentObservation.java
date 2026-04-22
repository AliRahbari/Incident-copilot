package com.incident.copilot.core.domain;

/**
 * A single concrete fact extracted from the incident input — e.g. an exception
 * type, a thread state, a GC metric. Deliberately wraps a String rather than
 * aliasing it, so future fields (source line, severity weight) can be added
 * without touching callers.
 */
public record IncidentObservation(String description) {

    public IncidentObservation {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("IncidentObservation description must not be blank");
        }
    }
}
