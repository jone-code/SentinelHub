package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record CreatePolicyRequest(
        @NotBlank String name,
        @NotBlank String type,
        @NotNull Map<String, Object> content,
        Integer priority,
        Map<String, Object> scope
) {
    public int resolvedPriority() {
        return priority != null ? priority : 100;
    }
}
