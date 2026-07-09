package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.ai.llm")
public record AiLlmProperties(
        boolean enabled,
        String baseUrl,
        String apiKey,
        String model
) {
    public boolean isConfigured() {
        return enabled && apiKey != null && !apiKey.isBlank() && baseUrl != null && !baseUrl.isBlank();
    }
}
