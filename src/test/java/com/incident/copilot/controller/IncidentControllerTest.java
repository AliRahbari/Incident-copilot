package com.incident.copilot.controller;

import com.incident.copilot.domain.IncidentAnalysis;
import com.incident.copilot.domain.IncidentCategory;
import com.incident.copilot.domain.IncidentInput;
import com.incident.copilot.domain.IncidentObservation;
import com.incident.copilot.domain.IncidentSeverity;
import com.incident.copilot.domain.PossibleCause;
import com.incident.copilot.domain.RecommendedAction;
import com.incident.copilot.service.IncidentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private IncidentAnalysisService analysisService;

    @Test
    void analyze_blankInput_returns400() throws Exception {
        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyze_missingInput_returns400() throws Exception {
        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyze_inputTooLong_returns400() throws Exception {
        String longInput = "x".repeat(50_001);
        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"input\": \"" + longInput + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyze_validInput_returns200() throws Exception {
        when(analysisService.analyze(any(IncidentInput.class)))
                .thenReturn(new IncidentAnalysis(
                        "Database connection pool exhausted",
                        IncidentSeverity.UNKNOWN,
                        IncidentCategory.UNKNOWN,
                        List.of(new IncidentObservation("HikariPool-1 - Connection is not available")),
                        List.of(new PossibleCause(
                                "Too many concurrent queries",
                                "high",
                                List.of("HikariPool-1 - Connection is not available, request timed out after 30000ms")
                        )),
                        List.of(new RecommendedAction("Increase HikariCP maximumPoolSize in application.yml"))
                ));

        mockMvc.perform(post("/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"input": "ERROR: connection pool exhausted"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary").value("Database connection pool exhausted"))
                .andExpect(jsonPath("$.observations[0]").value("HikariPool-1 - Connection is not available"))
                .andExpect(jsonPath("$.possibleCauses[0].cause").value("Too many concurrent queries"))
                .andExpect(jsonPath("$.possibleCauses[0].confidence").value("high"))
                .andExpect(jsonPath("$.possibleCauses[0].evidence[0]").exists())
                .andExpect(jsonPath("$.nextSteps[0]").value("Increase HikariCP maximumPoolSize in application.yml"));
    }
}
