package com.incident.copilot.core.analysis;

import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;

import java.util.Objects;

/**
 * Result of classifying an incident into a ({@link IncidentSeverity}, {@link IncidentCategory})
 * pair. Both values are always non-null; {@code UNKNOWN} is used as the explicit
 * "could not classify" signal.
 */
public record Classification(IncidentSeverity severity, IncidentCategory category) {

    public static final Classification UNKNOWN =
            new Classification(IncidentSeverity.UNKNOWN, IncidentCategory.UNKNOWN);

    public Classification {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
    }
}
