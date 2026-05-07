package com.incident.copilot.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.core.analysis.IncidentAnalysisService;
import com.incident.copilot.core.analysis.IncidentClassifier;
import com.incident.copilot.core.analysis.LlmClient;
import com.incident.copilot.core.sink.IncidentSink;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.util.List;

@AutoConfiguration(afterName = {
        "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration",
        "org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration"
})
@EnableConfigurationProperties(IncidentCopilotProperties.class)
@ConditionalOnProperty(prefix = "incident-copilot", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IncidentCopilotAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public IncidentClassifier incidentClassifier() {
        return new IncidentClassifier();
    }

    @Bean
    @ConditionalOnBean(LlmClient.class)
    @ConditionalOnMissingBean
    public IncidentAnalysisService incidentAnalysisService(LlmClient llmClient,
                                                           ObjectMapper objectMapper,
                                                           IncidentClassifier classifier) {
        return new IncidentAnalysisService(llmClient, objectMapper, classifier);
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "incident-copilot", name = "publish-metrics", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(IncidentMetrics.class)
    public IncidentMetrics micrometerIncidentMetrics(MeterRegistry meterRegistry) {
        return new MicrometerIncidentMetrics(meterRegistry);
    }

    @Bean
    @ConditionalOnMissingBean(IncidentMetrics.class)
    public IncidentMetrics noOpIncidentMetrics() {
        return new IncidentMetrics.NoOpIncidentMetrics();
    }

    @Bean
    @ConditionalOnMissingBean
    public IncidentSignalRecorder incidentSignalRecorder(IncidentMetrics metrics,
                                                         IncidentClassifier classifier,
                                                         ObjectProvider<IncidentSink> sinks) {
        List<IncidentSink> orderedSinks = sinks.orderedStream().toList();
        return new IncidentSignalRecorder(metrics, classifier, orderedSinks);
    }

    @Bean
    @ConditionalOnClass(HandlerExceptionResolver.class)
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    @ConditionalOnProperty(prefix = "incident-copilot", name = "capture-exceptions", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(IncidentExceptionCaptureResolver.class)
    public IncidentExceptionCaptureResolver incidentExceptionCaptureResolver(IncidentSignalRecorder recorder) {
        return new IncidentExceptionCaptureResolver(recorder);
    }
}
