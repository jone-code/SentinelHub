package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record UpdateNacPolicyRequest(
        @NotBlank String name,
        @Min(0) @Max(100) int minComplianceScore,
        @NotBlank String actionOnFail,
        String isolateVlan,
        Boolean enabled
) {
    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }
}
