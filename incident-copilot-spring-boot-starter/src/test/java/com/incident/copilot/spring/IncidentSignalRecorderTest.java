package com.incident.copilot.spring;

import com.incident.copilot.core.analysis.IncidentClassifier;
import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentObservation;
import com.incident.copilot.core.domain.IncidentSeverity;
import com.incident.copilot.core.domain.PossibleCause;
import com.incident.copilot.core.domain.RecommendedAction;
import com.incident.copilot.core.sink.IncidentSignal;
import com.incident.copilot.core.sink.IncidentSink;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.ConnectException;
import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
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

    @Test
    void capture_analysis_fansOutToSinkWithSignal() {
        IncidentSink sink = mock(IncidentSink.class);
        IncidentSignalRecorder r = new IncidentSignalRecorder(
                metrics, new IncidentClassifier(), List.of(sink));

        IncidentAnalysis analysis = analysisWith(IncidentSeverity.HIGH, IncidentCategory.DATABASE);
        r.capture(analysis);

        ArgumentCaptor<IncidentSignal> captor = ArgumentCaptor.forClass(IncidentSignal.class);
        verify(sink).accept(captor.capture());
        IncidentSignal signal = captor.getValue();
        assertEquals(IncidentSeverity.HIGH, signal.severity());
        assertEquals(IncidentCategory.DATABASE, signal.category());
        assertTrue(signal.analysisOpt().isPresent());
        assertSame(analysis, signal.analysisOpt().get());
        assertTrue(signal.throwableOpt().isEmpty());
    }

    @Test
    void capture_throwable_fansOutToSinkWithClassifiedSignal() {
        IncidentSink sink = mock(IncidentSink.class);
        IncidentSignalRecorder r = new IncidentSignalRecorder(
                metrics, new IncidentClassifier(), List.of(sink));
        SQLException ex = new SQLException("duplicate key");

        r.capture(ex);

        ArgumentCaptor<IncidentSignal> captor = ArgumentCaptor.forClass(IncidentSignal.class);
        verify(sink).accept(captor.capture());
        IncidentSignal signal = captor.getValue();
        assertEquals(IncidentSeverity.HIGH, signal.severity());
        assertEquals(IncidentCategory.DATABASE, signal.category());
        assertTrue(signal.throwableOpt().isPresent());
        assertSame(ex, signal.throwableOpt().get());
        assertTrue(signal.analysisOpt().isEmpty());
    }

    @Test
    void capture_fansOutToMultipleSinksInOrder() {
        IncidentSink first = mock(IncidentSink.class);
        IncidentSink second = mock(IncidentSink.class);
        IncidentSignalRecorder r = new IncidentSignalRecorder(
                metrics, new IncidentClassifier(), List.of(first, second));

        r.capture(analysisWith(IncidentSeverity.LOW, IncidentCategory.IO));

        verify(first).accept(any(IncidentSignal.class));
        verify(second).accept(any(IncidentSignal.class));
    }

    @Test
    void capture_failingSinkDoesNotBreakOtherSinks() {
        IncidentSink exploding = mock(IncidentSink.class);
        IncidentSink healthy = mock(IncidentSink.class);
        doThrow(new RuntimeException("jira down"))
                .when(exploding).accept(any(IncidentSignal.class));

        IncidentSignalRecorder r = new IncidentSignalRecorder(
                metrics, new IncidentClassifier(), List.of(exploding, healthy));

        // Must not propagate — capture is observation, it cannot break callers.
        r.capture(analysisWith(IncidentSeverity.HIGH, IncidentCategory.DATABASE));

        verify(exploding).accept(any(IncidentSignal.class));
        verify(healthy).accept(any(IncidentSignal.class));
        verify(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));
    }

    @Test
    void capture_withNoSinks_doesNothingBeyondMetrics() {
        IncidentSignalRecorder r = new IncidentSignalRecorder(
                metrics, new IncidentClassifier(), List.of());

        r.capture(analysisWith(IncidentSeverity.HIGH, IncidentCategory.DATABASE));

        verify(metrics).recordCaptured(eq(IncidentSeverity.HIGH), eq(IncidentCategory.DATABASE));
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
