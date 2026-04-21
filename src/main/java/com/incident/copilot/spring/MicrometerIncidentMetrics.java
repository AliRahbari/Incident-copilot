package com.incident.copilot.spring;

import com.incident.copilot.domain.IncidentCategory;
import com.incident.copilot.domain.IncidentSeverity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

/**
 * Micrometer-backed {@link IncidentMetrics}. Publishes a single counter,
 * {@value #CAPTURES_METRIC}, tagged with {@value #TAG_SEVERITY} and
 * {@value #TAG_CATEGORY} on every sample. One metric, one tag set — queries
 * over a tag still give a breakdown, and sum-over-tags gives the total.
 */
public class MicrometerIncidentMetrics implements IncidentMetrics {

    /** Single counter for every captured incident signal. */
    public static final String CAPTURES_METRIC = "incident_copilot.captures";

    /** Tag key carrying the {@link IncidentSeverity} of the captured signal. */
    public static final String TAG_SEVERITY = "severity";

    /** Tag key carrying the {@link IncidentCategory} of the captured signal. */
    public static final String TAG_CATEGORY = "category";

    private final MeterRegistry registry;

    public MicrometerIncidentMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void recordCaptured(IncidentSeverity severity, IncidentCategory category) {
        IncidentSeverity s = severity == null ? IncidentSeverity.UNKNOWN : severity;
        IncidentCategory c = category == null ? IncidentCategory.UNKNOWN : category;
        Counter.builder(CAPTURES_METRIC)
                .description("Captured incident signals, tagged by severity and category.")
                .tag(TAG_SEVERITY, s.name())
                .tag(TAG_CATEGORY, c.name())
                .register(registry)
                .increment();
    }
}
