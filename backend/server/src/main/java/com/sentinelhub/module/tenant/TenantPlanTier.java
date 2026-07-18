package com.sentinelhub.module.tenant;

public enum TenantPlanTier {
    STARTER("starter"),
    BUSINESS("business"),
    ENTERPRISE("enterprise");

    private final String code;

    TenantPlanTier(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    public static TenantPlanTier fromCode(String code) {
        if (code == null || code.isBlank()) {
            return STARTER;
        }
        for (TenantPlanTier tier : values()) {
            if (tier.code.equalsIgnoreCase(code)) {
                return tier;
            }
        }
        return STARTER;
    }
}
