package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record AssignMdmProfileRequest(
        @NotBlank String deviceId,
        @NotBlank String profileId
) {
}
