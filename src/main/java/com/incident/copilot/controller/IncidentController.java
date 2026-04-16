package com.incident.copilot.controller;

import com.incident.copilot.dto.AnalyzeRequest;
import com.incident.copilot.dto.AnalyzeResponse;
import com.incident.copilot.service.IncidentAnalysisService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentAnalysisService analysisService;

    public IncidentController(IncidentAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalyzeResponse> analyze(@Valid @RequestBody AnalyzeRequest request) {
        log.info("POST /analyze (json) — input length: {}", request.input().length());
        AnalyzeResponse response = analysisService.analyze(request.input());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/analyze", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<AnalyzeResponse> analyzeText(@RequestBody String input) {
        log.info("POST /analyze (text) — input length: {}", input.length());
        AnalyzeResponse response = analysisService.analyze(input);
        return ResponseEntity.ok(response);
    }
}
