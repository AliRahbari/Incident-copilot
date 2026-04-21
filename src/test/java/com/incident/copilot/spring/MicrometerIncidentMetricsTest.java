package com.incident.copilot.spring;

import com.incident.copilot.domain.IncidentCategory;
import com.incident.copilot.domain.IncidentSeverity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the metric name, tag keys and tag values that
 * {@link MicrometerIncidentMetrics} publishes. These are the observability
 * contract a future starter exposes, so changes here should be deliberate.
 */
class MicrometerIncidentMetricsTest {

    private MeterRegistry registry;
    private MicrometerIncidentMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerIncidentMetrics(registry);
    }

    @Test
    void recordCaptured_emitsCounterWithSeverityAndCategoryTags() {
        metrics.recordCaptured(IncidentSeverity.HIGH, IncidentCategory.DATABASE);

        Counter counter = registry.find(MicrometerIncidentMetrics.CAPTURES_METRIC)
                .tag(MicrometerIncidentMetrics.TAG_SEVERITY, "HIGH")
                .tag(MicrometerIncidentMetrics.TAG_CATEGORY, "DATABASE")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordCaptured_nullSeverityAndCategory_fallBackToUnknown() {
        metrics.recordCaptured(null, null);

        Counter counter = registry.find(MicrometerIncidentMetrics.CAPTURES_METRIC)
                .tag(MicrometerIncidentMetrics.TAG_SEVERITY, "UNKNOWN")
                .tag(MicrometerIncidentMetrics.TAG_CATEGORY, "UNKNOWN")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    void recordCaptured_sameTags_reusesCounter() {
        metrics.recordCaptured(IncidentSeverity.HIGH, IncidentCategory.DATABASE);
        metrics.recordCaptured(IncidentSeverity.HIGH, IncidentCategory.DATABASE);
        metrics.recordCaptured(IncidentSeverity.HIGH, IncidentCategory.DATABASE);

        long counterCount = registry.find(MicrometerIncidentMetrics.CAPTURES_METRIC).counters().size();
        double total = registry.find(MicrometerIncidentMetrics.CAPTURES_METRIC)
                .tag(MicrometerIncidentMetrics.TAG_SEVERITY, "HIGH")
                .tag(MicrometerIncidentMetrics.TAG_CATEGORY, "DATABASE")
                .counter()
                .count();

        assertThat(counterCount).isEqualTo(1);
        assertThat(total).isEqualTo(3.0);
    }

    @Test
    void recordCaptured_differentTags_createSeparateSeries() {
        metrics.recordCaptured(IncidentSeverity.HIGH, IncidentCategory.DATABASE);
        metrics.recordCaptured(IncidentSeverity.LOW, IncidentCategory.NETWORK);

        assertThat(registry.find(MicrometerIncidentMetrics.CAPTURES_METRIC).counters()).hasSize(2);
        assertThat(sumOfAllCaptureCounters()).isEqualTo(2.0);
    }

    private double sumOfAllCaptureCounters() {
        return registry.find(MicrometerIncidentMetrics.CAPTURES_METRIC)
                .counters()
                .stream()
                .mapToDouble(Counter::count)
                .sum();
    }
}
