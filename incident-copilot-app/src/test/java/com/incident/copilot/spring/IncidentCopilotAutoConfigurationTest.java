package com.incident.copilot.spring;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins down the conditional bean wiring of {@link IncidentCopilotAutoConfiguration}
 * — which beans appear for which {@code incident-copilot.*} property combinations.
 * These conditions form the stable starter contract, so regressions here are
 * user-visible.
 */
class IncidentCopilotAutoConfigurationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(IncidentCopilotAutoConfiguration.class));

    @Test
    void defaults_withMeterRegistry_wireMicrometerMetricsRecorderAndResolver() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IncidentMetrics.class);
                    assertThat(ctx.getBean(IncidentMetrics.class))
                            .isInstanceOf(MicrometerIncidentMetrics.class);
                    assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
                    assertThat(ctx).hasSingleBean(IncidentExceptionCaptureResolver.class);
                });
    }

    @Test
    void defaults_withoutMeterRegistry_fallBackToNoOpMetrics() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IncidentMetrics.class);
            assertThat(ctx.getBean(IncidentMetrics.class))
                    .isInstanceOf(IncidentMetrics.NoOpIncidentMetrics.class);
            assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
            assertThat(ctx).hasSingleBean(IncidentExceptionCaptureResolver.class);
        });
    }

    @Test
    void enabledFalse_skipsAllBeans() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withPropertyValues("incident-copilot.enabled=false")
                .run(ctx -> {
                    assertThat(ctx).doesNotHaveBean(IncidentMetrics.class);
                    assertThat(ctx).doesNotHaveBean(IncidentSignalRecorder.class);
                    assertThat(ctx).doesNotHaveBean(IncidentExceptionCaptureResolver.class);
                });
    }

    @Test
    void publishMetricsFalse_usesNoOpEvenWithMeterRegistry() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withPropertyValues("incident-copilot.publish-metrics=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IncidentMetrics.class);
                    assertThat(ctx.getBean(IncidentMetrics.class))
                            .isInstanceOf(IncidentMetrics.NoOpIncidentMetrics.class);
                    assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
                    assertThat(ctx).hasSingleBean(IncidentExceptionCaptureResolver.class);
                });
    }

    @Test
    void captureExceptionsFalse_skipsResolverOnly() {
        runner.withUserConfiguration(MeterRegistryConfig.class)
                .withPropertyValues("incident-copilot.capture-exceptions=false")
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IncidentMetrics.class);
                    assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
                    assertThat(ctx).doesNotHaveBean(IncidentExceptionCaptureResolver.class);
                });
    }

    @Test
    void nonWebApplication_skipsResolverButKeepsMetricsAndRecorder() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(IncidentCopilotAutoConfiguration.class))
                .withUserConfiguration(MeterRegistryConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IncidentMetrics.class);
                    assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
                    assertThat(ctx).doesNotHaveBean(IncidentExceptionCaptureResolver.class);
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
