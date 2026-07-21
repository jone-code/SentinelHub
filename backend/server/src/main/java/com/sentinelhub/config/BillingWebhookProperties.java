package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.admin.ws.billing-webhook")
public record BillingWebhookProperties(
        boolean enabled,
        String url,
        String secret
) {
}
