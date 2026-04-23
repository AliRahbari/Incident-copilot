package com.incident.copilot.spring;

import com.incident.copilot.core.analysis.IncidentClassifier;
import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentObservation;
import com.incident.copilot.core.domain.IncidentSeverity;
import com.incident.copilot.core.domain.PossibleCause;
import com.incident.copilot.core.domain.RecommendedAction;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link IncidentSignalRecorder}. Pins down:
 *  - analysis capture forwards severity/category to the metrics SPI
 *  - throwable capture is classified via {@link IncidentClassifier} so metrics
 *    get meaningful tags rather than UNKNOWN/UNKNOWN
 *  - null inputs are silently ignored
 *  - failures in the metrics backend never propagate (non-invasive guarantee)
 */
class IncidentSignalRecorderTest {

    private final IncidentMetrics metrics = mock(IncidentMetrics.class);
    private final IncidentSignalRecorder recorder =
            new IncidentSignalRecorder(metrics, new IncidentClassifier());

    @Test
    void capture_analysis_forwardsSeverityAndCategory() {
        recorder.capture(analysisWith(IncidentSeverity.HIGH, IncidentCategory.DATABASE));

        verify(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));
    }

    @Test
    void capture_sqlException_isTaggedAsDatabase() {
        recorder.capture(new SQLException("duplicate key"));

        verify(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));
    }

    @Test
    void capture_connectException_isTaggedAsNetwork() {
        recorder.capture(new ConnectException("connection refused"));

        verify(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.NETWORK));
    }

    @Test
    void capture_outOfMemory_isTaggedAsCriticalMemory() {
        recorder.capture(new OutOfMemoryError("Java heap space"));

        verify(metrics).recordCaptured(eq(IncidentSeverity.CRITICAL), eq(IncidentCategory.MEMORY));
    }

    @Test
    void capture_unknownThrowable_fallsBackToMediumUnknown() {
        recorder.capture(new RuntimeException("boom"));

        verify(metrics).recordCaptured(eq(IncidentSeverity.MEDIUM), eq(IncidentCategory.UNKNOWN));
    }

    @Test
    void capture_nullAnalysis_isNoOp() {
        recorder.capture((IncidentAnalysis) null);

        verifyNoInteractions(metrics);
    }

    @Test
    void capture_nullThrowable_isNoOp() {
        recorder.capture((Throwable) null);

        verifyNoInteractions(metrics);
    }

    @Test
    void capture_metricsBackendThrows_isSwallowed() {
        doThrow(new RuntimeException("registry offline"))
                .when(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));

        // Must not propagate — capture is observation, it cannot break callers.
        recorder.capture(analysisWith(IncidentSeverity.HIGH, IncidentCategory.DATABASE));

        verify(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));
    }

    @Test
    void capture_throwableWhenMetricsBackendThrows_isSwallowed() {
        doThrow(new RuntimeException("registry offline"))
                .when(metrics).recordCaptured(eq(IncidentSeverity.MEDIUM), eq(IncidentCategory.UNKNOWN));

        recorder.capture(new IllegalStateException("x"));

        verify(metrics).recordCaptured(eq(IncidentSeverity.MEDIUM), eq(IncidentCategory.UNKNOWN));
        verify(metrics, never()).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));
    }

    private static IncidentAnalysis analysisWith(IncidentSeverity severity, IncidentCategory category) {
        return new IncidentAnalysis(
                "summary",
                severity,
                category,
                List.of(new IncidentObservation("obs")),
                List.of(new PossibleCause("cause", "high", List.of("evidence"))),
                List.of(new RecommendedAction("next step"))
        );
    }
}
