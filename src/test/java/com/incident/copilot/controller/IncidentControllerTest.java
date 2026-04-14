package com.incident.copilot.controller;

import com.incident.copilot.dto.AnalyzeResponse;
import com.incident.copilot.dto.PossibleCause;
import com.incident.copilot.service.IncidentAnalysisService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(IncidentController.class)
class IncidentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
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
        when(analysisService.analyze(anyString()))
                .thenReturn(new AnalyzeResponse(
                        "Database connection pool exhausted",
                        List.of("HikariPool-1 - Connection is not available"),
                        List.of(new PossibleCause(
                                "Too many concurrent queries",
                                "high",
                                List.of("HikariPool-1 - Connection is not available, request timed out after 30000ms")
                        )),
                        List.of("Increase HikariCP maximumPoolSize in application.yml")
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
