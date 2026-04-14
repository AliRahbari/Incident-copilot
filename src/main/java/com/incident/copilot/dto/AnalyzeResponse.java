package com.incident.copilot.dto;

import java.util.List;

public record AnalyzeResponse(
        String summary,
        List<String> possibleCauses,
        List<String> nextSteps
) {}
