package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrgUnitRequest(
        @NotBlank String name,
        String parentId
) {}
