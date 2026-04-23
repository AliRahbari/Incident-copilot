package com.incident.copilot.spring;

import com.incident.copilot.core.analysis.Classification;
import com.incident.copilot.core.analysis.IncidentClassifier;
import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single entry point for "something incident-worthy happened". Callers hand
 * over either a finished analysis or a raw Throwable; the recorder decides how
 * to fan out to metrics (and, in a later phase, other sinks).
 *
 * <p>Throwables are passed through an {@link IncidentClassifier} so metrics are
 * tagged with a meaningful severity/category instead of UNKNOWN/UNKNOWN.
 */
public class IncidentSignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(IncidentSignalRecorder.class);

    private final IncidentMetrics metrics;
    private final IncidentClassifier classifier;

    public IncidentSignalRecorder(IncidentMetrics metrics) {
        this(metrics, new IncidentClassifier());
    }

    public IncidentSignalRecorder(IncidentMetrics metrics, IncidentClassifier classifier) {
        this.metrics = metrics;
        this.classifier = classifier;
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
        Classification c = classifier.classify(throwable);
        safeRecord(c.severity(), c.category());
    }

    private void safeRecord(IncidentSeverity severity, IncidentCategory category) {
        try {
            metrics.recordCaptured(severity, category);
        } catch (RuntimeException ex) {
            log.warn("Failed to record incident signal", ex);
        }
    }
}
