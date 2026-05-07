package com.incident.copilot.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.core.analysis.IncidentAnalysisService;
import com.incident.copilot.core.analysis.IncidentClassifier;
import com.incident.copilot.core.analysis.LlmClient;
import com.incident.copilot.core.sink.IncidentSink;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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

    @Test
    void withLlmClientAndObjectMapper_wiresIncidentAnalysisService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        IncidentCopilotAutoConfiguration.class))
                .withUserConfiguration(LlmClientConfig.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(IncidentAnalysisService.class));
    }

    @Test
    void withoutLlmClient_doesNotWireIncidentAnalysisService() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        IncidentCopilotAutoConfiguration.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(IncidentAnalysisService.class));
    }

    @Test
    void defaults_wireIncidentClassifier() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(IncidentClassifier.class));
    }

    @Test
    void defaults_withNoSinks_recorderStillWires() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
            assertThat(ctx.getBeansOfType(IncidentSink.class)).isEmpty();
        });
    }

    @Test
    void registeredSinks_areInjectedIntoRecorder() {
        runner.withUserConfiguration(TwoSinksConfig.class)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(IncidentSignalRecorder.class);
                    assertThat(ctx.getBeansOfType(IncidentSink.class)).hasSize(2);

                    IncidentSignalRecorder recorder = ctx.getBean(IncidentSignalRecorder.class);
                    recorder.capture(new RuntimeException("x"));

                    RecordingSink a = ctx.getBean("sinkA", RecordingSink.class);
                    RecordingSink b = ctx.getBean("sinkB", RecordingSink.class);
                    assertThat(a.count).isEqualTo(1);
                    assertThat(b.count).isEqualTo(1);
                });
    }

    @Test
    void consumerProvidedClassifier_winsOverStarter() {
        IncidentClassifier custom = new IncidentClassifier();
        runner.withBean(IncidentClassifier.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(IncidentClassifier.class)).isSameAs(custom));
    }

    @Test
    void consumerProvidedIncidentAnalysisService_winsOverStarter() {
        IncidentAnalysisService custom = new IncidentAnalysisService(mock(LlmClient.class), new ObjectMapper());
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        IncidentCopilotAutoConfiguration.class))
                .withUserConfiguration(LlmClientConfig.class)
                .withBean(IncidentAnalysisService.class, () -> custom)
                .run(ctx -> assertThat(ctx.getBean(IncidentAnalysisService.class)).isSameAs(custom));
    }

    @Configuration(proxyBeanMethods = false)
    static class MeterRegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LlmClientConfig {
        @Bean
        LlmClient llmClient() {
            return (system, user) -> "{}";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TwoSinksConfig {
        @Bean
        RecordingSink sinkA() {
            return new RecordingSink();
        }

        @Bean
        RecordingSink sinkB() {
            return new RecordingSink();
        }
    }

    static class RecordingSink implements IncidentSink {
        int count;

        @Override
        public void accept(com.incident.copilot.core.sink.IncidentSignal signal) {
            count++;
        }
    }
}
