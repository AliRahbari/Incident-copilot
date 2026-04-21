package com.incident.copilot.domain;

/**
 * Raw material handed to the incident analyzer: logs, stack traces, or free-form
 * error text. Framework-agnostic — no Spring, Jackson, or validation annotations,
 * so this type is safe to expose from a reusable core library.
 *
 * <p>Additional metadata (source, timestamp, correlation id, tags) can be added as
 * a separate optional component later without changing this type's constructor
 * contract.
 */
public record IncidentInput(String content) {

    public IncidentInput {
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("IncidentInput content must not be blank");
        }
    }
}
