package com.sentinelhub.module.policy.domain;

import java.time.Instant;

public record Policy(
        String id,
        String tenantId,
        String name,
        String type,
        String status,
        int priority,
        String scopeJson,
        String contentJson,
        String createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}
