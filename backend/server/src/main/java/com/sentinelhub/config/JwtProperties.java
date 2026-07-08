package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.jwt")
public record JwtProperties(String secret, int expirationHours) {
}
