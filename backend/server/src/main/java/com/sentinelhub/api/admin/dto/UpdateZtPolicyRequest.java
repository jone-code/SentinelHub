package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateZtPolicyRequest(
        @NotBlank String name,
        @Min(0) @Max(100) int complianceWeight,
        @Min(0) @Max(100) int nacWeight,
        @Min(0) @Max(100) int eventWeight,
        @Min(0) @Max(100) int minTrustScore,
        Boolean enabled
) {
    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }
}
