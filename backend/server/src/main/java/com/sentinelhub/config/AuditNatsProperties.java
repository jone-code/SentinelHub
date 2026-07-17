package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.audit.nats")
public record AuditNatsProperties(
        boolean enabled,
        String url,
        String subject,
        String stream,
        String durable,
        int batchSize,
        int maxInFlight,
        int fetchTimeoutMs,
        int backlogBackoffMs,
        long maxStreamBytes
) {
    public AuditNatsProperties {
        if (url == null || url.isBlank()) {
            url = "nats://localhost:4222";
        }
        if (subject == null || subject.isBlank()) {
            subject = "sentinel.audit.events";
        }
        if (stream == null || stream.isBlank()) {
            stream = "SENTINEL_AUDIT";
        }
        if (durable == null || durable.isBlank()) {
            durable = "audit-writer";
        }
        if (batchSize <= 0) {
            batchSize = 50;
        }
        if (maxInFlight <= 0) {
            maxInFlight = 2;
        }
        if (fetchTimeoutMs <= 0) {
            fetchTimeoutMs = 2000;
        }
        if (backlogBackoffMs <= 0) {
            backlogBackoffMs = 500;
        }
    }
}
