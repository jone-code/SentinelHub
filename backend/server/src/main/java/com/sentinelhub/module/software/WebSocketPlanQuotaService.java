package com.sentinelhub.module.software;

import com.sentinelhub.config.WebSocketLimitsProperties;
import com.sentinelhub.config.WebSocketPlanQuotaProperties;
import com.sentinelhub.module.tenant.TenantPlanRepository;
import com.sentinelhub.module.tenant.TenantPlanTier;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class WebSocketPlanQuotaService {

    private final WebSocketPlanQuotaProperties planQuotas;
    private final WebSocketLimitsProperties fallbackLimits;
    private final TenantPlanRepository tenantPlanRepository;

    public WebSocketPlanQuotaService(WebSocketPlanQuotaProperties planQuotas,
                                     WebSocketLimitsProperties fallbackLimits,
                                     TenantPlanRepository tenantPlanRepository) {
        this.planQuotas = planQuotas;
        this.fallbackLimits = fallbackLimits;
        this.tenantPlanRepository = tenantPlanRepository;
    }

    public int maxConnectionsForTenant(String tenantId) {
        if (!planQuotas.enabled()) {
            return fallbackLimits.maxConnectionsPerTenant();
        }
        TenantPlanTier tier = tenantPlanRepository.findPlanTier(tenantId);
        return tierQuota(tier).maxConnections();
    }

    public int maxEventsPerSecondForTenant(String tenantId) {
        if (!planQuotas.enabled()) {
            return fallbackLimits.maxEventsPerSecondPerTenant();
        }
        TenantPlanTier tier = tenantPlanRepository.findPlanTier(tenantId);
        return tierQuota(tier).maxEventsPerSecond();
    }

    public Map<String, Object> quotaSnapshot(String tenantId) {
        TenantPlanTier tier = tenantPlanRepository.findPlanTier(tenantId);
        WebSocketPlanQuotaProperties.TierQuota quota = tierQuota(tier);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan_tier", tier.code());
        out.put("plan_quotas_enabled", planQuotas.enabled());
        out.put("max_connections", maxConnectionsForTenant(tenantId));
        out.put("max_events_per_second", maxEventsPerSecondForTenant(tenantId));
        out.put("tier_defaults", Map.of(
                "max_connections", quota.maxConnections(),
                "max_events_per_second", quota.maxEventsPerSecond()
        ));
        return out;
    }

    public Map<String, WebSocketPlanQuotaProperties.TierQuota> allTierQuotas() {
        return planQuotas.tiers();
    }

    public boolean isPlanQuotasEnabled() {
        return planQuotas.enabled();
    }

    private WebSocketPlanQuotaProperties.TierQuota tierQuota(TenantPlanTier tier) {
        return planQuotas.tiers().getOrDefault(tier.code(), planQuotas.tiers().get("starter"));
    }
}
