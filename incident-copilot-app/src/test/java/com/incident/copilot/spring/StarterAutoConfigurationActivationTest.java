package com.incident.copilot.spring;

import com.incident.copilot.client.OpenAiClient;
import com.incident.copilot.core.analysis.IncidentAnalysisService;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.servlet.HandlerExceptionResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the starter is actually consumed by this app: loading the full Spring
 * Boot context (no manual {@code @ImportAutoConfiguration}) must yield every
 * bean the starter promises, plus the {@link IncidentAnalysisService} wired
 * from the app's {@code LlmClient}. If the auto-configuration ever fails to
 * activate through the imports file, this test fails.
 */
@SpringBootTest
@TestPropertySource(properties = "openai.api-key=test-key")
class StarterAutoConfigurationActivationTest {

    @Autowired
    private ApplicationContext context;

    @MockitoBean
    private OpenAiClient openAiClient;

    @Test
    void starterBeansAreRegisteredInFullApplicationContext() {
        assertThat(context.getBean(IncidentCopilotProperties.class)).isNotNull();
        assertThat(context.getBean(IncidentMetrics.class))
                .isInstanceOf(MicrometerIncidentMetrics.class);
        assertThat(context.getBean(IncidentSignalRecorder.class)).isNotNull();
        assertThat(context.getBeansOfType(HandlerExceptionResolver.class).values())
                .anyMatch(IncidentExceptionCaptureResolver.class::isInstance);
    }

    @Test
    void starterWiresIncidentAnalysisServiceFromAppProvidedLlmClient() {
        assertThat(context.getBean(IncidentAnalysisService.class)).isNotNull();
    }

    @Test
    void starterDefaultsLeaveMeterRegistryProvidedByActuatorIntact() {
        assertThat(context.getBean(MeterRegistry.class)).isNotNull();
    }
}
