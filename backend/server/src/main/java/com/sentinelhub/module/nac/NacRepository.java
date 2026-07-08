package com.sentinelhub.module.nac;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class NacRepository {

    private final JdbcTemplate jdbc;

    public NacRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insertPolicy(String tenantId, String name, int minScore, String actionOnFail,
                               String isolateVlan, boolean enabled) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO nac_policies (id, tenant_id, name, min_compliance_score, action_on_fail, "
                        + "isolate_vlan, enabled) VALUES (?,?,?,?,?,?,?)",
                id, tenantId, name, minScore, actionOnFail, isolateVlan, enabled ? 1 : 0);
        return id;
    }

    public void updatePolicy(String tenantId, String id, String name, int minScore, String actionOnFail,
                             String isolateVlan, boolean enabled) {
        jdbc.update(
                "UPDATE nac_policies SET name = ?, min_compliance_score = ?, action_on_fail = ?, "
                        + "isolate_vlan = ?, enabled = ?, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE tenant_id = ? AND id = ?",
                name, minScore, actionOnFail, isolateVlan, enabled ? 1 : 0, tenantId, id);
    }

    public Optional<Map<String, Object>> findPolicyByTenant(String tenantId) {
        var list = jdbc.queryForList(
                "SELECT id, name, min_compliance_score, action_on_fail, isolate_vlan, enabled, "
                        + "created_at, updated_at FROM nac_policies WHERE tenant_id = ? LIMIT 1",
                tenantId);
        return list.stream().findFirst();
    }

    public boolean hasPolicy(String tenantId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM nac_policies WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null && c > 0;
    }

    public void upsertDeviceStatus(String tenantId, String deviceId, String policyId, String accessState,
                                   Integer complianceScore, String detailJson, Instant evaluatedAt) {
        jdbc.update(
                "INSERT INTO nac_device_status (device_id, tenant_id, policy_id, access_state, compliance_score, "
                        + "detail, evaluated_at) VALUES (?,?,?,?,?,CAST(? AS JSON),?) "
                        + "ON DUPLICATE KEY UPDATE policy_id = VALUES(policy_id), access_state = VALUES(access_state), "
                        + "compliance_score = VALUES(compliance_score), detail = VALUES(detail), "
                        + "evaluated_at = VALUES(evaluated_at)",
                deviceId, tenantId, policyId, accessState, complianceScore, detailJson, Timestamp.from(evaluatedAt));
    }

    public Optional<Map<String, Object>> findDeviceStatus(String deviceId) {
        var list = jdbc.queryForList(
                "SELECT device_id, access_state, compliance_score, CAST(detail AS CHAR) AS detail, evaluated_at "
                        + "FROM nac_device_status WHERE device_id = ?",
                deviceId);
        return list.stream().findFirst();
    }

    public List<Map<String, Object>> listDeviceStatusByTenant(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT s.device_id, s.access_state, s.compliance_score, s.evaluated_at, "
                        + "CAST(s.detail AS CHAR) AS detail, d.hostname, d.agent_id "
                        + "FROM nac_device_status s JOIN devices d ON d.id = s.device_id "
                        + "WHERE s.tenant_id = ? ORDER BY s.evaluated_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countDeviceStatus(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nac_device_status WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null ? c : 0;
    }
}
