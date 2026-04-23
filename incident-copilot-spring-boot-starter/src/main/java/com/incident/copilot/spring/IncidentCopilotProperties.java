package com.incident.copilot.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunables for the Spring integration layer. Designed so a future
 * {@code incident-copilot-spring-boot-starter} can expose exactly these keys
 * without renaming.
 */
@ConfigurationProperties(prefix = "incident-copilot")
public class IncidentCopilotProperties {

    /** Master switch for the entire Spring integration layer. */
    private boolean enabled = true;

    /** Whether to record a metric signal for exceptions that bubble out of MVC handlers. */
    private boolean captureExceptions = true;

    /** Whether to publish Micrometer metrics for captured incidents. */
    private boolean publishMetrics = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isCaptureExceptions() {
        return captureExceptions;
    }

    public void setCaptureExceptions(boolean captureExceptions) {
        this.captureExceptions = captureExceptions;
    }

    public boolean isPublishMetrics() {
        return publishMetrics;
    }

    public void setPublishMetrics(boolean publishMetrics) {
        this.publishMetrics = publishMetrics;
    }
}
