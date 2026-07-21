package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.admin.ws.plan-approval")
public record PlanApprovalProperties(
        boolean enabled,
        boolean autoApproveDowngrade,
        int requiredApprovals
) {
    public PlanApprovalProperties {
        if (requiredApprovals <= 0) {
            requiredApprovals = 1;
        }
    }
}
