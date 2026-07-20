package com.sentinelhub.module.tenant;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class TenantPlanChangeRequestRepository {

    public record PlanChangeRequest(
            String id,
            String tenantId,
            String requestedBy,
            String fromTier,
            String toTier,
            String status,
            int monthlyPriceCents,
            String currency,
            String billingNote,
            String reviewedBy,
            String reviewNote,
            Instant createdAt,
            Instant reviewedAt
    ) {
    }

    private static final RowMapper<PlanChangeRequest> ROW_MAPPER = (rs, rowNum) -> new PlanChangeRequest(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("requested_by"),
            rs.getString("from_tier"),
            rs.getString("to_tier"),
            rs.getString("status"),
            rs.getInt("monthly_price_cents"),
            rs.getString("currency"),
            rs.getString("billing_note"),
            rs.getString("reviewed_by"),
            rs.getString("review_note"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("reviewed_at") != null ? rs.getTimestamp("reviewed_at").toInstant() : null
    );

    private final JdbcTemplate jdbc;

    public TenantPlanChangeRequestRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insert(String tenantId, String requestedBy, String fromTier, String toTier,
                         int monthlyPriceCents, String currency, String billingNote) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO tenant_plan_change_requests "
                        + "(id, tenant_id, requested_by, from_tier, to_tier, status, monthly_price_cents, currency, billing_note) "
                        + "VALUES (?,?,?,?,?,?,?,?,?)",
                id, tenantId, requestedBy, fromTier, toTier, "pending",
                monthlyPriceCents, currency, billingNote);
        return id;
    }

    public Optional<PlanChangeRequest> findById(String tenantId, String id) {
        var rows = jdbc.query(
                "SELECT * FROM tenant_plan_change_requests WHERE tenant_id = ? AND id = ?",
                ROW_MAPPER, tenantId, id);
        return rows.stream().findFirst();
    }

    public List<PlanChangeRequest> listByTenant(String tenantId, String status) {
        if (status == null || status.isBlank()) {
            return jdbc.query(
                    "SELECT * FROM tenant_plan_change_requests WHERE tenant_id = ? ORDER BY created_at DESC LIMIT 50",
                    ROW_MAPPER, tenantId);
        }
        return jdbc.query(
                "SELECT * FROM tenant_plan_change_requests WHERE tenant_id = ? AND status = ? ORDER BY created_at DESC LIMIT 50",
                ROW_MAPPER, tenantId, status);
    }

    public boolean hasPending(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM tenant_plan_change_requests WHERE tenant_id = ? AND status = 'pending'",
                Integer.class, tenantId);
        return count != null && count > 0;
    }

    public void approve(String tenantId, String id, String reviewedBy, String reviewNote) {
        jdbc.update(
                "UPDATE tenant_plan_change_requests SET status = 'approved', reviewed_by = ?, "
                        + "review_note = ?, reviewed_at = ? WHERE tenant_id = ? AND id = ? AND status = 'pending'",
                reviewedBy, reviewNote, Timestamp.from(Instant.now()), tenantId, id);
    }

    public void reject(String tenantId, String id, String reviewedBy, String reviewNote) {
        jdbc.update(
                "UPDATE tenant_plan_change_requests SET status = 'rejected', reviewed_by = ?, "
                        + "review_note = ?, reviewed_at = ? WHERE tenant_id = ? AND id = ? AND status = 'pending'",
                reviewedBy, reviewNote, Timestamp.from(Instant.now()), tenantId, id);
    }
}
