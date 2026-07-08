package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreateMdmProfileRequest(
        @NotBlank String name,
        @NotBlank String profileType,
        @NotNull Map<String, Object> content,
        Boolean enabled
) {
    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }
}
