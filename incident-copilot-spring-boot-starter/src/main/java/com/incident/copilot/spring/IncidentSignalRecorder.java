package com.incident.copilot.spring;

import com.incident.copilot.core.analysis.Classification;
import com.incident.copilot.core.analysis.IncidentClassifier;
import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentSeverity;
import com.incident.copilot.core.sink.IncidentSignal;
import com.incident.copilot.core.sink.IncidentSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Single entry point for "something incident-worthy happened". Callers hand
 * over either a finished analysis or a raw Throwable; the recorder records
 * metrics and fans out to any registered {@link IncidentSink}s.
 *
 * <p>Throwables are passed through an {@link IncidentClassifier} so metrics are
 * tagged with a meaningful severity/category instead of UNKNOWN/UNKNOWN.
 *
 * <p>A failing sink never breaks the caller: each sink is invoked inside a
 * try/catch that logs and swallows. Sinks are invoked after metrics, so an
 * exploding sink does not suppress the counter.
 */
public class IncidentSignalRecorder {

    private static final Logger log = LoggerFactory.getLogger(IncidentSignalRecorder.class);

    private final IncidentMetrics metrics;
    private final IncidentClassifier classifier;
    private final List<IncidentSink> sinks;

    public IncidentSignalRecorder(IncidentMetrics metrics) {
        this(metrics, new IncidentClassifier(), List.of());
    }

    public IncidentSignalRecorder(IncidentMetrics metrics, IncidentClassifier classifier) {
        this(metrics, classifier, List.of());
    }

    public IncidentSignalRecorder(IncidentMetrics metrics,
                                  IncidentClassifier classifier,
                                  List<IncidentSink> sinks) {
        this.metrics = metrics;
        this.classifier = classifier;
        this.sinks = sinks == null ? List.of() : List.copyOf(sinks);
    }

    public void capture(IncidentAnalysis analysis) {
        if (analysis == null) {
            return;
        }
        IncidentSeverity severity = analysis.severity();
        IncidentCategory category = analysis.category();
        safeRecord(severity, category);
        fanOut(IncidentSignal.ofAnalysis(severity, category, analysis));
    }

    public void capture(Throwable throwable) {
        if (throwable == null) {
            return;
        }
        Classification c = classifier.classify(throwable);
        safeRecord(c.severity(), c.category());
        fanOut(IncidentSignal.ofThrowable(c.severity(), c.category(), throwable));
    }

    private void safeRecord(IncidentSeverity severity, IncidentCategory category) {
        try {
            metrics.recordCaptured(severity, category);
        } catch (RuntimeException ex) {
            log.warn("Failed to record incident signal", ex);
        }
    }

    private void fanOut(IncidentSignal signal) {
        if (sinks.isEmpty()) {
            return;
        }
        for (IncidentSink sink : sinks) {
            try {
                sink.accept(signal);
            } catch (RuntimeException ex) {
                log.warn("Incident sink {} failed; continuing", sink.getClass().getName(), ex);
            }
        }
    }
}
