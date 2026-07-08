package com.sentinelhub.module.compliance;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ComplianceRepository {

    private final JdbcTemplate jdbc;

    public ComplianceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insertBaseline(String tenantId, String name, String framework, String rulesJson) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO compliance_baselines (id, tenant_id, name, framework, rules) VALUES (?,?,?,?,CAST(? AS JSON))",
                id, tenantId, name, framework, rulesJson);
        return id;
    }

    public Optional<String> findDefaultBaselineId(String tenantId) {
        var ids = jdbc.query(
                "SELECT id FROM compliance_baselines WHERE tenant_id = ? ORDER BY created_at ASC LIMIT 1",
                (rs, n) -> rs.getString("id"), tenantId);
        return ids.stream().findFirst();
    }

    public boolean hasBaseline(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM compliance_baselines WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null && c > 0;
    }

    public void insertResult(String tenantId, String deviceId, String baselineId, int score,
                             int passed, int failed, String detailsJson, Instant scannedAt) {
        jdbc.update(
                "INSERT INTO compliance_results (id, tenant_id, device_id, baseline_id, score, passed, failed, details, scanned_at) "
                        + "VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),?)",
                UUID.randomUUID().toString(), tenantId, deviceId, baselineId, score, passed, failed,
                detailsJson, Timestamp.from(scannedAt));
    }

    public Optional<Map<String, Object>> findLatestByDevice(String deviceId) {
        var list = jdbc.queryForList(
                "SELECT score, passed, failed, CAST(details AS CHAR) AS details, scanned_at "
                        + "FROM compliance_results WHERE device_id = ? ORDER BY scanned_at DESC LIMIT 1",
                deviceId);
        return list.stream().findFirst();
    }

    public List<Map<String, Object>> listByTenant(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT r.id, r.score, r.passed, r.failed, r.scanned_at, d.hostname, d.agent_id, "
                        + "CAST(r.details AS CHAR) AS details "
                        + "FROM compliance_results r JOIN devices d ON d.id = r.device_id "
                        + "WHERE r.tenant_id = ? AND r.scanned_at = ("
                        + "  SELECT MAX(r2.scanned_at) FROM compliance_results r2 WHERE r2.device_id = r.device_id"
                        + ") ORDER BY r.scanned_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countDevicesWithResults(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(DISTINCT device_id) FROM compliance_results WHERE tenant_id = ?",
                Integer.class, tenantId);
        return c != null ? c : 0;
    }

    public Double averageScore(String tenantId) {
        var list = jdbc.queryForList(
                "SELECT r.score FROM compliance_results r "
                        + "INNER JOIN (SELECT device_id, MAX(scanned_at) AS max_at FROM compliance_results "
                        + "WHERE tenant_id = ? GROUP BY device_id) latest "
                        + "ON r.device_id = latest.device_id AND r.scanned_at = latest.max_at",
                tenantId);
        if (list.isEmpty()) return 0.0;
        return list.stream().mapToInt(r -> ((Number) r.get("score")).intValue()).average().orElse(0);
    }
}
