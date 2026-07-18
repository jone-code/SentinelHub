package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.audit.timeline-sync")
public record TimelineSyncProperties(
        boolean enabled,
        long intervalMs,
        int batchSize
) {
    public TimelineSyncProperties {
        if (intervalMs <= 0) {
            intervalMs = 60_000;
        }
        if (batchSize <= 0) {
            batchSize = 200;
        }
    }
}
