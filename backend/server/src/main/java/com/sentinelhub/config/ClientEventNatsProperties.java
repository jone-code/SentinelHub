package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.client-events.nats")
public record ClientEventNatsProperties(
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
    public ClientEventNatsProperties {
        if (url == null || url.isBlank()) {
            url = "nats://localhost:4222";
        }
        if (subject == null || subject.isBlank()) {
            subject = "sentinel.client.events";
        }
        if (stream == null || stream.isBlank()) {
            stream = "SENTINEL_CLIENT_EVENTS";
        }
        if (durable == null || durable.isBlank()) {
            durable = "client-events-writer";
        }
        if (batchSize <= 0) {
            batchSize = 100;
        }
        if (maxInFlight <= 0) {
            maxInFlight = 3;
        }
        if (fetchTimeoutMs <= 0) {
            fetchTimeoutMs = 2000;
        }
        if (backlogBackoffMs <= 0) {
            backlogBackoffMs = 500;
        }
    }
}
