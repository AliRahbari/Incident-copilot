package com.incident.copilot.core.sink;

import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentObservation;
import com.incident.copilot.core.domain.IncidentSeverity;
import com.incident.copilot.core.domain.PossibleCause;
import com.incident.copilot.core.domain.RecommendedAction;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins down the {@link IncidentSignal} value contract — which factory goes with
 * which optional field, and that severity/category/timestamp are always present.
 */
class IncidentSignalTest {

    @Test
    void ofAnalysis_carriesAnalysisAndNullThrowable() {
        IncidentAnalysis analysis = analysis();

        IncidentSignal signal = IncidentSignal.ofAnalysis(
                IncidentSeverity.HIGH, IncidentCategory.DATABASE, analysis);

        assertEquals(IncidentSeverity.HIGH, signal.severity());
        assertEquals(IncidentCategory.DATABASE, signal.category());
        assertTrue(signal.analysisOpt().isPresent());
        assertSame(analysis, signal.analysisOpt().get());
        assertFalse(signal.throwableOpt().isPresent());
        assertTrue(!signal.capturedAt().isAfter(Instant.now()));
    }

    @Test
    void ofThrowable_carriesThrowableAndNullAnalysis() {
        RuntimeException boom = new RuntimeException("boom");

        IncidentSignal signal = IncidentSignal.ofThrowable(
                IncidentSeverity.MEDIUM, IncidentCategory.UNKNOWN, boom);

        assertEquals(IncidentSeverity.MEDIUM, signal.severity());
        assertEquals(IncidentCategory.UNKNOWN, signal.category());
        assertTrue(signal.throwableOpt().isPresent());
        assertSame(boom, signal.throwableOpt().get());
        assertFalse(signal.analysisOpt().isPresent());
        assertTrue(!signal.capturedAt().isAfter(Instant.now()));
    }

    @Test
    void constructor_rejectsNullRequiredFields() {
        assertThrows(NullPointerException.class, () -> new IncidentSignal(
                null, IncidentCategory.UNKNOWN, null, null, Instant.now()));
        assertThrows(NullPointerException.class, () -> new IncidentSignal(
                IncidentSeverity.LOW, null, null, null, Instant.now()));
        assertThrows(NullPointerException.class, () -> new IncidentSignal(
                IncidentSeverity.LOW, IncidentCategory.UNKNOWN, null, null, null));
    }

    @Test
    void ofAnalysis_rejectsNullAnalysis() {
        assertThrows(NullPointerException.class, () -> IncidentSignal.ofAnalysis(
                IncidentSeverity.LOW, IncidentCategory.UNKNOWN, null));
    }

    @Test
    void ofThrowable_rejectsNullThrowable() {
        assertThrows(NullPointerException.class, () -> IncidentSignal.ofThrowable(
                IncidentSeverity.LOW, IncidentCategory.UNKNOWN, null));
    }

    private static IncidentAnalysis analysis() {
        return new IncidentAnalysis(
                "summary",
                IncidentSeverity.HIGH,
                IncidentCategory.DATABASE,
                List.of(new IncidentObservation("obs")),
                List.of(new PossibleCause("cause", "high", List.of("e"))),
                List.of(new RecommendedAction("next"))
        );
    }
}
