package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.client-events.nats")
public record ClientEventNatsProperties(
        boolean enabled,
        String url,
        String subject,
        String stream,
        String durable
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
    }
}
