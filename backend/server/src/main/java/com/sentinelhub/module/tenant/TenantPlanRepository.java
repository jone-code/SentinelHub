package com.sentinelhub.module.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class TenantPlanRepository {

    private final JdbcTemplate jdbc;

    public TenantPlanRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public TenantPlanTier findPlanTier(String tenantId) {
        var tiers = jdbc.query(
                "SELECT plan_tier FROM tenants WHERE id = ? AND status = 'active'",
                (rs, rowNum) -> TenantPlanTier.fromCode(rs.getString("plan_tier")),
                tenantId);
        return tiers.stream().findFirst().orElse(TenantPlanTier.STARTER);
    }

    public Optional<String> findPlanTierCode(String tenantId) {
        var tiers = jdbc.query(
                "SELECT plan_tier FROM tenants WHERE id = ?",
                (rs, rowNum) -> rs.getString("plan_tier"),
                tenantId);
        return tiers.stream().findFirst();
    }

    public void updatePlanTier(String tenantId, String planTier) {
        jdbc.update("UPDATE tenants SET plan_tier = ? WHERE id = ?", planTier, tenantId);
    }
}
