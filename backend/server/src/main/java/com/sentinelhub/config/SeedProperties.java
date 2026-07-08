package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.seed")
public record SeedProperties(
        boolean enabled,
        String adminEmail,
        String adminPassword,
        String tenantSlug,
        String registrationToken
) {
}
