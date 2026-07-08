package com.sentinelhub.module.device.domain;

import java.time.Instant;

public record Device(
        String id,
        String tenantId,
        String orgUnitId,
        String agentId,
        String hostname,
        String osType,
        String osVersion,
        String hardwareId,
        String status,
        Instant lastSeenAt,
        Integer complianceScore,
        Instant createdAt
) {
}
