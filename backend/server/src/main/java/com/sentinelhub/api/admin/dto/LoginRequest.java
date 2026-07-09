package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank @Email String email,
        @NotBlank String password,
        String tenantSlug
) {
    public String resolvedTenantSlug() {
        return tenantSlug != null && !tenantSlug.isBlank() ? tenantSlug : "demo";
    }
}
