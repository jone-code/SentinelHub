package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.admin.ws.broadcast")
public record WebSocketBroadcastProperties(
        boolean enabled,
        String url,
        String subject
) {
    public WebSocketBroadcastProperties {
        if (url == null || url.isBlank()) {
            url = "nats://localhost:4222";
        }
        if (subject == null || subject.isBlank()) {
            subject = "sentinel.admin.ws.events";
        }
    }
}
