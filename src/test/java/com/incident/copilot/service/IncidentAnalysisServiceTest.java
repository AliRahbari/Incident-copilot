package com.incident.copilot.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.incident.copilot.client.OpenAiClient;
import com.incident.copilot.dto.AnalyzeResponse;
import com.incident.copilot.exception.LlmResponseException;
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
    private OpenAiClient openAiClient;

    private IncidentAnalysisService service;

    @BeforeEach
    void setUp() {
        service = new IncidentAnalysisService(openAiClient, new ObjectMapper());
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
        when(openAiClient.chat(anyString(), anyString())).thenReturn(validJson);

        AnalyzeResponse response = service.analyze("some error log");

        assertEquals("OOM in pod-xyz", response.summary());
        assertEquals(2, response.observations().size());
        assertEquals(1, response.possibleCauses().size());
        assertEquals("high", response.possibleCauses().get(0).confidence());
        assertEquals(1, response.possibleCauses().get(0).evidence().size());
        assertEquals(1, response.nextSteps().size());
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
        when(openAiClient.chat(anyString(), anyString())).thenReturn(fencedJson);

        AnalyzeResponse response = service.analyze("timeout error log");

        assertEquals("Connection timeout", response.summary());
        assertNotNull(response.observations());
        assertNotNull(response.possibleCauses());
        assertEquals("medium", response.possibleCauses().get(0).confidence());
        assertNotNull(response.nextSteps());
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
        when(openAiClient.chat(anyString(), anyString())).thenReturn(fencedJson);

        AnalyzeResponse response = service.analyze("disk full error");

        assertEquals("Disk full", response.summary());
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
        when(openAiClient.chat(anyString(), anyString())).thenReturn(json);

        AnalyzeResponse response = service.analyze("some input");

        assertEquals(2, response.possibleCauses().size());
        assertEquals("high", response.possibleCauses().get(0).confidence());
        assertEquals("low", response.possibleCauses().get(1).confidence());
        assertEquals(2, response.possibleCauses().get(0).evidence().size());
    }

    @Test
    void analyze_malformedJson_throwsLlmResponseException() {
        when(openAiClient.chat(anyString(), anyString())).thenReturn("This is not JSON at all");

        assertThrows(LlmResponseException.class, () -> service.analyze("some input"));
    }

    @Test
    void analyze_incompleteJson_throwsLlmResponseException() {
        when(openAiClient.chat(anyString(), anyString())).thenReturn("{\"summary\": ");

        assertThrows(LlmResponseException.class, () -> service.analyze("some input"));
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
