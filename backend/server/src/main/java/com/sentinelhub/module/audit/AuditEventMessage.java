package com.sentinelhub.module.audit;

import java.util.Map;

public record AuditEventMessage(
        String id,
        String tenantId,
        String actorType,
        String actorId,
        String action,
        String resource,
        String resourceId,
        String detailJson,
        String ip
) {}
