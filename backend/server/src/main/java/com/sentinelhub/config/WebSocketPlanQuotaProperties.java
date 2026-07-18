package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.Map;

@ConfigurationProperties(prefix = "sentinelhub.admin.ws.plan-quotas")
public record WebSocketPlanQuotaProperties(
        boolean enabled,
        Map<String, TierQuota> tiers
) {
    public WebSocketPlanQuotaProperties {
        if (tiers == null || tiers.isEmpty()) {
            tiers = Map.of(
                    "starter", new TierQuota(5, 20),
                    "business", new TierQuota(20, 50),
                    "enterprise", new TierQuota(100, 200)
            );
        }
    }

    public record TierQuota(int maxConnections, int maxEventsPerSecond) {
        public TierQuota {
            if (maxConnections <= 0) {
                maxConnections = 5;
            }
            if (maxEventsPerSecond <= 0) {
                maxEventsPerSecond = 20;
            }
        }
    }
}
