package com.incident.copilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.core.analysis.IncidentAnalysisService;
import com.incident.copilot.core.analysis.LlmClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires framework-agnostic {@code core} components as Spring beans from the
 * application side. Depends on the {@link LlmClient} interface from core so
 * {@link IncidentAnalysisService} stays free of Spring annotations.
 */
@Configuration(proxyBeanMethods = false)
public class CoreBeansConfiguration {

    @Bean
    public IncidentAnalysisService incidentAnalysisService(LlmClient llmClient,
                                                           ObjectMapper objectMapper) {
        return new IncidentAnalysisService(llmClient, objectMapper);
    }
}
