package com.incident.copilot.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI incidentCopilotOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Incident Copilot API")
                        .description("MVP backend service that analyzes logs and stack traces using LLM")
                        .version("0.1.0"));
    }
}
