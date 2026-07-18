package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.admin.ws.limits")
public record WebSocketLimitsProperties(
        int maxConnectionsPerTenant,
        int maxEventsPerSecondPerTenant,
        int maxConnectionsGlobal
) {
    public WebSocketLimitsProperties {
        if (maxConnectionsPerTenant <= 0) {
            maxConnectionsPerTenant = 10;
        }
        if (maxEventsPerSecondPerTenant <= 0) {
            maxEventsPerSecondPerTenant = 50;
        }
        if (maxConnectionsGlobal <= 0) {
            maxConnectionsGlobal = 100;
        }
    }
}
