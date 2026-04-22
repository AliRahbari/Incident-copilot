package com.incident.copilot.spring;

import com.incident.copilot.client.OpenAiClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end check that the actuator {@code /metrics} endpoint is exposed and
 * that our {@value MicrometerIncidentMetrics#CAPTURES_METRIC} counter is
 * reachable with its {@code severity} and {@code category} tags after a real
 * capture has happened.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "openai.api-key=test-key",
        "management.endpoints.web.exposure.include=metrics"
})
class ActuatorMetricsEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OpenAiClient openAiClient;

    @Test
    void captureMetricIsVisibleOnActuatorEndpointWithExpectedTags() throws Exception {
        when(openAiClient.chat(anyString(), anyString())).thenReturn("""
                {
                  "summary": "Disk full",
                  "observations": ["No space left on device"],
                  "possibleCauses": [
                    {"cause": "Log rotation disabled", "confidence": "high", "evidence": ["ENOSPC"]}
                  ],
                  "nextSteps": ["Free disk space"]
                }
                """);

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\": \"ERROR: disk full\"}"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names", hasItem(MicrometerIncidentMetrics.CAPTURES_METRIC)));

        mockMvc.perform(get("/actuator/metrics/" + MicrometerIncidentMetrics.CAPTURES_METRIC))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(MicrometerIncidentMetrics.CAPTURES_METRIC))
                .andExpect(jsonPath("$.availableTags[*].tag",
                        hasItems(MicrometerIncidentMetrics.TAG_SEVERITY,
                                MicrometerIncidentMetrics.TAG_CATEGORY)));
    }
}
