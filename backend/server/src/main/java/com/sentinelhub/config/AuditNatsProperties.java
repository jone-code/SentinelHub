package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.audit.nats")
public record AuditNatsProperties(
        boolean enabled,
        String url,
        String subject,
        String stream,
        String durable
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
    }
}
