package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateDeviceGroupRequest(
        @NotBlank String name,
        String description
) {}
