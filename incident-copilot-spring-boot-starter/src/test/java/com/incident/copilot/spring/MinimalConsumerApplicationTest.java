package com.incident.copilot.spring;

import com.incident.copilot.core.analysis.IncidentAnalysisService;
import com.incident.copilot.core.analysis.LlmClient;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the starter is consumable by an <em>arbitrary</em> Spring Boot
 * application — not just the shipped {@code incident-copilot-app}. A minimal
 * {@code @SpringBootApplication} declared here, plus a single {@link LlmClient}
 * bean, must be enough for the full starter surface to light up via the
 * {@code META-INF/spring/...AutoConfiguration.imports} file. No explicit
 * {@code @Import} or {@code @EnableXxx} is used on purpose.
 */
@SpringBootTest(classes = MinimalConsumerApplicationTest.MinimalConsumerApplication.class)
class MinimalConsumerApplicationTest {

    @Autowired
    private ApplicationContext context;

    @Test
    void starterActivatesFromImportsFileWithOnlyAnLlmClientProvided() {
        assertThat(context.getBean(IncidentCopilotProperties.class)).isNotNull();
        assertThat(context.getBean(IncidentAnalysisService.class)).isNotNull();
        assertThat(context.getBean(IncidentSignalRecorder.class)).isNotNull();
        assertThat(context.getBean(IncidentMetrics.class))
                .isInstanceOf(MicrometerIncidentMetrics.class);
        assertThat(context.getBeansOfType(HandlerExceptionResolver.class).values())
                .anyMatch(IncidentExceptionCaptureResolver.class::isInstance);
    }

    @SpringBootApplication
    static class MinimalConsumerApplication {

        @Bean
        LlmClient llmClient() {
            return (system, user) -> "{}";
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
