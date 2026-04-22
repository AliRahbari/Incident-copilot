package com.incident.copilot.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.client.OpenAiClient;
import com.incident.copilot.core.analysis.IncidentAnalysisService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires framework-agnostic {@code core} components as Spring beans from the
 * application side. Keeps {@link IncidentAnalysisService} free of Spring
 * annotations so it can move to a standalone {@code incident-copilot-core}
 * module unchanged.
 */
@Configuration(proxyBeanMethods = false)
public class CoreBeansConfiguration {

    @Bean
    public IncidentAnalysisService incidentAnalysisService(OpenAiClient openAiClient,
                                                           ObjectMapper objectMapper) {
        return new IncidentAnalysisService(openAiClient, objectMapper);
    }
}
