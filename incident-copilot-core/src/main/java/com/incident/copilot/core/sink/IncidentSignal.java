package com.incident.copilot.core.sink;

import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable value handed to every {@link IncidentSink} when an incident is
 * captured. Carries the already-classified {@code severity}/{@code category}
 * plus whichever of the two originating inputs was available — a finished
 * {@link IncidentAnalysis} or a raw {@link Throwable}. Exactly one of those
 * two is present.
 *
 * <p>The split keeps sinks simple: a sink that only wants to format analyses
 * can ignore the throwable path, and vice versa, without duplicating
 * classification or losing the original context.
 */
public record IncidentSignal(
        IncidentSeverity severity,
        IncidentCategory category,
        IncidentAnalysis analysis,
        Throwable throwable,
        Instant capturedAt
) {

    public IncidentSignal {
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(capturedAt, "capturedAt");
    }

    public static IncidentSignal ofAnalysis(IncidentSeverity severity,
                                            IncidentCategory category,
                                            IncidentAnalysis analysis) {
        Objects.requireNonNull(analysis, "analysis");
        return new IncidentSignal(severity, category, analysis, null, Instant.now());
    }

    public static IncidentSignal ofThrowable(IncidentSeverity severity,
                                             IncidentCategory category,
                                             Throwable throwable) {
        Objects.requireNonNull(throwable, "throwable");
        return new IncidentSignal(severity, category, null, throwable, Instant.now());
    }

    public Optional<IncidentAnalysis> analysisOpt() {
        return Optional.ofNullable(analysis);
    }

    public Optional<Throwable> throwableOpt() {
        return Optional.ofNullable(throwable);
    }
}
