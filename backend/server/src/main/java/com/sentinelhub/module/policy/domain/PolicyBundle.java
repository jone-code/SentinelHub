package com.sentinelhub.module.policy.domain;

import java.time.Instant;

public record PolicyBundle(
        String tenantId,
        String version,
        String contentJson,
        String contentHash,
        Instant publishedAt
) {
}
