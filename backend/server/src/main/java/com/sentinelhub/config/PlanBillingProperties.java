package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "sentinelhub.admin.ws.plan-billing")
public record PlanBillingProperties(
        boolean enabled,
        String currency,
        Map<String, Integer> monthlyPriceCents
) {
    public PlanBillingProperties {
        if (currency == null || currency.isBlank()) {
            currency = "CNY";
        }
        if (monthlyPriceCents == null || monthlyPriceCents.isEmpty()) {
            monthlyPriceCents = Map.of(
                    "starter", 0,
                    "business", 29900,
                    "enterprise", 99900
            );
        }
    }

    public int priceForTier(String tier) {
        return monthlyPriceCents.getOrDefault(tier, 0);
    }
}
