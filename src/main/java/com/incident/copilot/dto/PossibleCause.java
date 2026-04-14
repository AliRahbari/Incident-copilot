package com.incident.copilot.dto;

import java.util.List;

public record PossibleCause(
        String cause,
        String confidence,
        List<String> evidence
) {}
