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
                  "possibleCauses": ["Memory leak in cache layer"],
                  "nextSteps": ["Restart the pod", "Check heap dump"]
                }
                """;
        when(openAiClient.chat(anyString(), anyString())).thenReturn(validJson);

        AnalyzeResponse response = service.analyze("some error log");

        assertEquals("OOM in pod-xyz", response.summary());
        assertEquals(1, response.possibleCauses().size());
        assertEquals(2, response.nextSteps().size());
    }

    @Test
    void analyze_jsonWrappedInMarkdownFences_parseSuccessfully() {
        String fencedJson = """
                ```json
                {
                  "summary": "Connection timeout",
                  "possibleCauses": ["DNS resolution failure"],
                  "nextSteps": ["Check /etc/resolv.conf"]
                }
                ```
                """;
        when(openAiClient.chat(anyString(), anyString())).thenReturn(fencedJson);

        AnalyzeResponse response = service.analyze("timeout error log");

        assertEquals("Connection timeout", response.summary());
        assertNotNull(response.possibleCauses());
        assertNotNull(response.nextSteps());
    }

    @Test
    void analyze_markdownFencesWithoutLanguageTag_parseSuccessfully() {
        String fencedJson = """
                ```
                {
                  "summary": "Disk full",
                  "possibleCauses": ["Log rotation disabled"],
                  "nextSteps": ["Free disk space"]
                }
                ```
                """;
        when(openAiClient.chat(anyString(), anyString())).thenReturn(fencedJson);

        AnalyzeResponse response = service.analyze("disk full error");

        assertEquals("Disk full", response.summary());
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
