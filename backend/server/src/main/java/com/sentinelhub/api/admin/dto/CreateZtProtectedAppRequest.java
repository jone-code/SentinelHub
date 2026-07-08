package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record CreateZtProtectedAppRequest(
        @NotBlank String name,
        @NotBlank String appIdentifier,
        @Min(0) @Max(100) int minTrustScore,
        Boolean enabled
) {
    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }
}
