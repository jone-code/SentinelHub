package com.sentinelhub.module.software;

public record ClientEventMessage(
        String id,
        String tenantId,
        String deviceId,
        String clientId,
        String eventType,
        String severity,
        String detailJson
) {}
