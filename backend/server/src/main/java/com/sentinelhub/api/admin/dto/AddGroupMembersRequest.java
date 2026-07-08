package com.sentinelhub.api.admin.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AddGroupMembersRequest(
        @NotEmpty List<String> deviceIds
) {}
