package com.sentinelhub.module.identity.domain;

import java.time.Instant;

public record User(
        String id,
        String tenantId,
        String orgUnitId,
        String email,
        String name,
        String passwordHash,
        String status,
        Instant createdAt
) {
}
