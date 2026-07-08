package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdatePolicyRequest(
        @NotBlank String name,
        @NotNull Map<String, Object> content,
        Integer priority
) {
    public int resolvedPriority() {
        return priority != null ? priority : 100;
    }
}
