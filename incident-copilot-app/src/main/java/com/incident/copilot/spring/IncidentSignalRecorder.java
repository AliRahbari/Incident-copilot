package com.incident.copilot.spring;

import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point for "something incident-worthy happened". Callers hand
 * over either a finished analysis or a raw Throwable; the recorder decides how
 * to fan out to metrics (and, in a later phase, other sinks).
 */
public class IncidentSignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(IncidentSignalRecorder.class);

    private final IncidentMetrics metrics;

    public IncidentSignalRecorder(IncidentMetrics metrics) {
        this.metrics = metrics;
    }

    public void capture(IncidentAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        safeRecord(analysis.severity(), analysis.category());
    }

    public void capture(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        safeRecord(IncidentSeverity.UNKNOWN, IncidentCategory.UNKNOWN);
    }

    private void safeRecord(IncidentSeverity severity, IncidentCategory category) {
        try {
            metrics.recordCaptured(severity, category);
        } catch (RuntimeException ex) {
            log.warn("Failed to record incident signal", ex);
        }
    }
}
