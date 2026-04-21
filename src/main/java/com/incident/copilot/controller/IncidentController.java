package com.incident.copilot.controller;

import com.incident.copilot.domain.IncidentAnalysis;
import com.incident.copilot.domain.IncidentInput;
import com.incident.copilot.dto.AnalyzeRequest;
import com.incident.copilot.dto.AnalyzeResponse;
import com.incident.copilot.dto.IncidentAnalysisMapper;
import com.incident.copilot.service.IncidentAnalysisService;
import com.incident.copilot.spring.IncidentSignalRecorder;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IncidentController {

    private static final Logger log = LoggerFactory.getLogger(IncidentController.class);

    private final IncidentAnalysisService analysisService;
    private final ObjectProvider<IncidentSignalRecorder> recorderProvider;

    public IncidentController(IncidentAnalysisService analysisService,
                              ObjectProvider<IncidentSignalRecorder> recorderProvider) {
        this.analysisService = analysisService;
        this.recorderProvider = recorderProvider;
    }

    @PostMapping(value = "/analyze", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<AnalyzeResponse> analyze(@Valid @RequestBody AnalyzeRequest request) {
        log.info("POST /analyze (json) — input length: {}", request.input().length());
        IncidentAnalysis analysis = analysisService.analyze(new IncidentInput(request.input()));
        recorderProvider.ifAvailable(r -> r.capture(analysis));
        return ResponseEntity.ok(IncidentAnalysisMapper.toResponse(analysis));
    }

    @PostMapping(value = "/analyze", consumes = MediaType.TEXT_PLAIN_VALUE)
    public ResponseEntity<AnalyzeResponse> analyzeText(@RequestBody String input) {
        log.info("POST /analyze (text) — input length: {}", input.length());
        IncidentAnalysis analysis = analysisService.analyze(new IncidentInput(input));
        recorderProvider.ifAvailable(r -> r.capture(analysis));
        return ResponseEntity.ok(IncidentAnalysisMapper.toResponse(analysis));
    }
}
