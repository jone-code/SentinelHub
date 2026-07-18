package com.sentinelhub.module.tenant;

import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class TenantPlanService {

    private final TenantPlanRepository tenantPlanRepository;

    public TenantPlanService(TenantPlanRepository tenantPlanRepository) {
        this.tenantPlanRepository = tenantPlanRepository;
    }

    public Map<String, Object> updatePlanTier(String tenantId, String planTierCode) {
        TenantPlanTier tier = resolveTier(planTierCode);
        tenantPlanRepository.updatePlanTier(tenantId, tier.code());
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("plan_tier", tier.code());
        out.put("message", "plan tier updated");
        return out;
    }

    private static TenantPlanTier resolveTier(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("plan_tier is required");
        }
        for (TenantPlanTier tier : TenantPlanTier.values()) {
            if (tier.code().equalsIgnoreCase(code)) {
                return tier;
            }
        }
        throw new IllegalArgumentException("invalid plan_tier: " + code);
    }
}
