package com.incident.copilot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(
        @NotBlank(message = "Input must not be blank")
        @Size(max = 50_000, message = "Input must not exceed 50,000 characters")
        String input
) {}
