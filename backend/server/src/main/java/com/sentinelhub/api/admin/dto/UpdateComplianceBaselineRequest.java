package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

public record UpdateComplianceBaselineRequest(
        @NotBlank String name,
        @NotEmpty List<Map<String, Object>> rules
) {}
