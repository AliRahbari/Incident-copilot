package com.incident.copilot.domain;

/**
 * A concrete follow-up step suggested by the analyzer (e.g. capture a heap dump,
 * check a config file). Wrapped as a record so future fields — target role,
 * automation hook, external ticket link — can be added without a breaking change.
 */
public record RecommendedAction(String description) {

    public RecommendedAction {
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("RecommendedAction description must not be blank");
        }
    }
}
