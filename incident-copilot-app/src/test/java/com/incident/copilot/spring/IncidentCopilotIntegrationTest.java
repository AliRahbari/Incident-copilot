package com.incident.copilot.spring;

import com.incident.copilot.controller.GlobalExceptionHandler;
import com.incident.copilot.controller.IncidentController;
import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentInput;
import com.incident.copilot.core.domain.IncidentObservation;
import com.incident.copilot.core.domain.IncidentSeverity;
import com.incident.copilot.core.domain.PossibleCause;
import com.incident.copilot.core.domain.RecommendedAction;
import com.incident.copilot.core.analysis.IncidentAnalysisService;
import com.incident.copilot.core.exception.LlmClientException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the Spring integration layer end-to-end through MockMvc:
 *  - happy-path POST /analyze increments the capture counter with the
 *    expected severity/category tags
 *  - malformed JSON still returns a clean 400 even though the exception
 *    capture resolver is in the chain (records a signal, does not hijack
 *    the response)
 *
 * A {@link SimpleMeterRegistry} is injected so counters can be asserted
 * without pulling in actuator.
 */
@WebMvcTest(IncidentController.class)
@ImportAutoConfiguration(IncidentCopilotAutoConfiguration.class)
class IncidentCopilotIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    @MockitoBean
    private IncidentAnalysisService analysisService;

    @TestConfiguration
    static class MetricsTestConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Test
    void validJson_happyPath_incrementsCaptureCounterWithUnknownTags() throws Exception {
        when(analysisService.analyze(any(IncidentInput.class)))
                .thenReturn(sampleAnalysis());

        double before = countWithUnknownTags();

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input": "ERROR: connection pool exhausted"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").exists());

        assertThat(countWithUnknownTags()).isEqualTo(before + 1.0);
    }

    @Test
    void malformedJson_returns400_andExceptionCaptureDoesNotBreakResponse() throws Exception {
        double before = countWithUnknownTags();

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not valid json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());

        // The resolver should have recorded the failure signal on its way
        // through, without swallowing the 400 response from the advice.
        // Throwable-based capture always tags severity=UNKNOWN, category=UNKNOWN.
        assertThat(countWithUnknownTags()).isEqualTo(before + 1.0);
    }

    /**
     * When the analysis service throws from inside the controller, the
     * {@link GlobalExceptionHandler} advice must still produce the expected
     * 5xx response, and the capture resolver must record the signal exactly
     * once. This is the non-invasive guarantee on the real controller path.
     */
    @Test
    void controllerThrows_adviceStillProducesResponse_andSignalIsRecordedOnce() throws Exception {
        when(analysisService.analyze(any(IncidentInput.class)))
                .thenThrow(new LlmClientException("upstream failed", new RuntimeException()));

        double before = countWithUnknownTags();

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input": "ERROR: connection pool exhausted"}
                                """))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").exists());

        assertThat(countWithUnknownTags()).isEqualTo(before + 1.0);
    }

    private double countWithUnknownTags() {
        Counter counter = meterRegistry.find(MicrometerIncidentMetrics.CAPTURES_METRIC)
                .tag(MicrometerIncidentMetrics.TAG_SEVERITY, IncidentSeverity.UNKNOWN.name())
                .tag(MicrometerIncidentMetrics.TAG_CATEGORY, IncidentCategory.UNKNOWN.name())
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    private static IncidentAnalysis sampleAnalysis() {
        return new IncidentAnalysis(
                "Database connection pool exhausted",
                IncidentSeverity.UNKNOWN,
                IncidentCategory.UNKNOWN,
                List.of(new IncidentObservation("HikariPool-1 - Connection is not available")),
                List.of(new PossibleCause(
                        "Too many concurrent queries",
                        "high",
                        List.of("request timed out after 30000ms")
                )),
                List.of(new RecommendedAction("Increase HikariCP maximumPoolSize"))
        );
    }
}
