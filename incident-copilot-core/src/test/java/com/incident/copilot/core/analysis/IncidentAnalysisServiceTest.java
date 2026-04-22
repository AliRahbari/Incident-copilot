package com.incident.copilot.core.analysis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.core.domain.IncidentAnalysis;
import com.incident.copilot.core.domain.IncidentCategory;
import com.incident.copilot.core.domain.IncidentInput;
import com.incident.copilot.core.domain.IncidentSeverity;
import com.incident.copilot.core.exception.LlmResponseException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IncidentAnalysisServiceTest {

    @Mock
    private LlmClient llmClient;

    private IncidentAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new IncidentAnalysisService(llmClient, new ObjectMapper());
    }

    @Test
    void analyze_validJson_parseSuccessfully() {
        String validJson = """
                {
                  "summary": "OOM in pod-xyz",
                  "observations": [
                    "java.lang.OutOfMemoryError at line 42",
                    "Heap usage reached 98% before crash"
                  ],
                  "possibleCauses": [
                    {
                      "cause": "Memory leak in cache layer",
                      "confidence": "high",
                      "evidence": ["OutOfMemoryError: Java heap space at CacheManager.put(CacheManager.java:42)"]
                    }
                  ],
                  "nextSteps": ["Capture heap dump with jmap -dump:live,format=b,file=heap.hprof <pid>"]
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(validJson);

        IncidentAnalysis analysis = service.analyze(new IncidentInput("some error log"));

        assertEquals("OOM in pod-xyz", analysis.summary());
        assertEquals(IncidentSeverity.UNKNOWN, analysis.severity());
        assertEquals(IncidentCategory.UNKNOWN, analysis.category());
        assertEquals(2, analysis.observations().size());
        assertEquals("java.lang.OutOfMemoryError at line 42", analysis.observations().get(0).description());
        assertEquals(1, analysis.possibleCauses().size());
        assertEquals("high", analysis.possibleCauses().get(0).confidence());
        assertEquals(1, analysis.possibleCauses().get(0).evidence().size());
        assertEquals(1, analysis.recommendedActions().size());
    }

    @Test
    void analyze_jsonWrappedInMarkdownFences_parseSuccessfully() {
        String fencedJson = """
                ```json
                {
                  "summary": "Connection timeout",
                  "observations": ["java.net.SocketTimeoutException after 30s"],
                  "possibleCauses": [
                    {
                      "cause": "DNS resolution failure",
                      "confidence": "medium",
                      "evidence": ["SocketTimeoutException on host db-primary.internal"]
                    }
                  ],
                  "nextSteps": ["Check /etc/resolv.conf"]
                }
                ```
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(fencedJson);

        IncidentAnalysis analysis = service.analyze(new IncidentInput("timeout error log"));

        assertEquals("Connection timeout", analysis.summary());
        assertNotNull(analysis.observations());
        assertNotNull(analysis.possibleCauses());
        assertEquals("medium", analysis.possibleCauses().get(0).confidence());
        assertNotNull(analysis.recommendedActions());
    }

    @Test
    void analyze_markdownFencesWithoutLanguageTag_parseSuccessfully() {
        String fencedJson = """
                ```
                {
                  "summary": "Disk full",
                  "observations": ["No space left on device in /var/log"],
                  "possibleCauses": [
                    {
                      "cause": "Log rotation disabled",
                      "confidence": "high",
                      "evidence": ["ENOSPC error writing to /var/log/app.log"]
                    }
                  ],
                  "nextSteps": ["Free disk space"]
                }
                ```
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(fencedJson);

        IncidentAnalysis analysis = service.analyze(new IncidentInput("disk full error"));

        assertEquals("Disk full", analysis.summary());
    }

    @Test
    void analyze_multipleCausesWithDifferentConfidence_parsesAll() {
        String json = """
                {
                  "summary": "Service returning 503s",
                  "observations": ["HTTP 503 on /api/users", "upstream connect timeout"],
                  "possibleCauses": [
                    {
                      "cause": "Backend pod is unresponsive",
                      "confidence": "high",
                      "evidence": ["upstream connect error", "503 Service Unavailable"]
                    },
                    {
                      "cause": "Resource limits too low",
                      "confidence": "low",
                      "evidence": ["No OOM events visible, but pod restarts detected"]
                    }
                  ],
                  "nextSteps": ["Check pod health", "Review resource limits"]
                }
                """;
        when(llmClient.chat(anyString(), anyString())).thenReturn(json);

        IncidentAnalysis analysis = service.analyze(new IncidentInput("some input"));

        assertEquals(2, analysis.possibleCauses().size());
        assertEquals("high", analysis.possibleCauses().get(0).confidence());
        assertEquals("low", analysis.possibleCauses().get(1).confidence());
        assertEquals(2, analysis.possibleCauses().get(0).evidence().size());
    }

    @Test
    void analyze_malformedJson_throwsLlmResponseException() {
        when(llmClient.chat(anyString(), anyString())).thenReturn("This is not JSON at all");

        assertThrows(LlmResponseException.class,
                () -> service.analyze(new IncidentInput("some input")));
    }

    @Test
    void analyze_incompleteJson_throwsLlmResponseException() {
        when(llmClient.chat(anyString(), anyString())).thenReturn("{\"summary\": ");

        assertThrows(LlmResponseException.class,
                () -> service.analyze(new IncidentInput("some input")));
    }

    @Test
    void analyze_blankInput_rejectedByDomain() {
        assertThrows(IllegalArgumentException.class,
                () -> service.analyze(new IncidentInput("")));
    }

    @Test
    void stripMarkdownFences_plainJson_unchanged() {
        String json = "{\"summary\": \"test\"}";
        assertEquals(json, IncidentAnalysisService.stripMarkdownFences(json));
    }

    @Test
    void stripMarkdownFences_withJsonTag_stripped() {
        String input = "```json\n{\"summary\": \"test\"}\n```";
        assertEquals("{\"summary\": \"test\"}", IncidentAnalysisService.stripMarkdownFences(input));
    }

    @Test
    void stripMarkdownFences_withoutTag_stripped() {
        String input = "```\n{\"summary\": \"test\"}\n```";
        assertEquals("{\"summary\": \"test\"}", IncidentAnalysisService.stripMarkdownFences(input));
    }

    @Test
    void stripMarkdownFences_withWhitespace_stripped() {
        String input = "  ```json\n{\"summary\": \"test\"}\n```  ";
        assertEquals("{\"summary\": \"test\"}", IncidentAnalysisService.stripMarkdownFences(input));
    }
}
