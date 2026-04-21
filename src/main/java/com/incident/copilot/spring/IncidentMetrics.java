package com.incident.copilot.spring;

import com.incident.copilot.domain.IncidentCategory;
import com.incident.copilot.domain.IncidentSeverity;

/**
 * SPI for recording incident capture metrics. Kept intentionally narrow so the
 * core domain does not depend on Micrometer, and so alternative backends
 * (OpenTelemetry, in-memory, test doubles) can plug in later.
 */
public interface IncidentMetrics {

    void recordCaptured(IncidentSeverity severity, IncidentCategory category);

    final class NoOpIncidentMetrics implements IncidentMetrics {
        @Override
        public void recordCaptured(IncidentSeverity severity, IncidentCategory category) {
        }
    }
}
