package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateRemoteSessionRequest(
        @NotBlank String deviceId,
        String reason,
        Boolean consentRequired
) {
    public boolean resolvedConsentRequired() {
        return consentRequired == null || consentRequired;
    }
}
