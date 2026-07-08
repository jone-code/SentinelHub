package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record UpdateDlpRuleRequest(
        @NotBlank String name,
        @NotBlank String channel,
        @NotBlank String action,
        @NotEmpty List<String> patterns,
        Boolean enabled,
        Integer priority
) {
    public boolean resolvedEnabled() {
        return enabled == null || enabled;
    }

    public int resolvedPriority() {
        return priority != null ? priority : 100;
    }
}
