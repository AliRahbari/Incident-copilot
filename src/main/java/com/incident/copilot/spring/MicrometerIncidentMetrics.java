package com.incident.copilot.spring;

import com.incident.copilot.domain.IncidentCategory;
import com.incident.copilot.domain.IncidentSeverity;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

public class MicrometerIncidentMetrics implements IncidentMetrics {

    static final String TOTAL = "incident_copilot.captured.total";
    static final String BY_SEVERITY = "incident_copilot.captured.by_severity";
    static final String BY_CATEGORY = "incident_copilot.captured.by_category";

    private final MeterRegistry registry;
    private final Counter total;

    public MicrometerIncidentMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.total = Counter.builder(TOTAL)
                .description("Total number of captured incident signals")
                .register(registry);
    }

    @Override
    public void recordCaptured(IncidentSeverity severity, IncidentCategory category) {
        IncidentSeverity s = severity == null ? IncidentSeverity.UNKNOWN : severity;
        IncidentCategory c = category == null ? IncidentCategory.UNKNOWN : category;
        total.increment();
        registry.counter(BY_SEVERITY, "severity", s.name()).increment();
        registry.counter(BY_CATEGORY, "category", c.name()).increment();
    }
}
