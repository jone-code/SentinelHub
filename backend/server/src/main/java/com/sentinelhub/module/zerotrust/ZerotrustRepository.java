package com.sentinelhub.module.zerotrust;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ZerotrustRepository {

    private final JdbcTemplate jdbc;

    public ZerotrustRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasPolicy(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM zt_policies WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null && count > 0;
    }

    public void insertPolicy(String tenantId, String name, int complianceWeight, int nacWeight,
                             int eventWeight, int minTrustScore, boolean enabled) {
        jdbc.update(
                "INSERT INTO zt_policies (id, tenant_id, name, compliance_weight, nac_weight, event_weight, "
                        + "min_trust_score, enabled) VALUES (?,?,?,?,?,?,?,?)",
                UUID.randomUUID().toString(), tenantId, name, complianceWeight, nacWeight, eventWeight,
                minTrustScore, enabled ? 1 : 0);
    }

    public Optional<Map<String, Object>> findPolicyByTenant(String tenantId) {
        var list = jdbc.queryForList("SELECT * FROM zt_policies WHERE tenant_id = ? LIMIT 1", tenantId);
        return list.stream().findFirst();
    }

    public void updatePolicy(String tenantId, String id, String name, int complianceWeight, int nacWeight,
                             int eventWeight, int minTrustScore, boolean enabled) {
        jdbc.update(
                "UPDATE zt_policies SET name=?, compliance_weight=?, nac_weight=?, event_weight=?, "
                        + "min_trust_score=?, enabled=? WHERE tenant_id=? AND id=?",
                name, complianceWeight, nacWeight, eventWeight, minTrustScore, enabled ? 1 : 0, tenantId, id);
    }

    public List<Map<String, Object>> listProtectedApps(String tenantId) {
        return jdbc.queryForList(
                "SELECT * FROM zt_protected_apps WHERE tenant_id = ? ORDER BY name", tenantId);
    }

    public Optional<Map<String, Object>> findProtectedApp(String tenantId, String id) {
        var list = jdbc.queryForList(
                "SELECT * FROM zt_protected_apps WHERE tenant_id = ? AND id = ?", tenantId, id);
        return list.stream().findFirst();
    }

    public String insertProtectedApp(String tenantId, String name, String appIdentifier,
                                     int minTrustScore, boolean enabled) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO zt_protected_apps (id, tenant_id, name, app_identifier, min_trust_score, enabled) "
                        + "VALUES (?,?,?,?,?,?)",
                id, tenantId, name, appIdentifier, minTrustScore, enabled ? 1 : 0);
        return id;
    }

    public void updateProtectedApp(String tenantId, String id, String name, String appIdentifier,
                                   int minTrustScore, boolean enabled) {
        jdbc.update(
                "UPDATE zt_protected_apps SET name=?, app_identifier=?, min_trust_score=?, enabled=? "
                        + "WHERE tenant_id=? AND id=?",
                name, appIdentifier, minTrustScore, enabled ? 1 : 0, tenantId, id);
    }

    public void insertTrustHistory(String tenantId, String deviceId, int trustScore, String factorsJson) {
        jdbc.update(
                "INSERT INTO zt_trust_history (id, tenant_id, device_id, trust_score, factors) VALUES (?,?,?,?,CAST(? AS JSON))",
                UUID.randomUUID().toString(), tenantId, deviceId, trustScore, factorsJson);
    }

    public List<Map<String, Object>> listTrustHistory(String tenantId, String deviceId, int limit) {
        return jdbc.queryForList(
                "SELECT trust_score, factors, created_at FROM zt_trust_history "
                        + "WHERE tenant_id = ? AND device_id = ? ORDER BY created_at DESC LIMIT ?",
                tenantId, deviceId, limit);
    }

    public List<Map<String, Object>> listDeviceTrustForAdmin(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT d.id AS device_id, d.agent_id, d.hostname, d.compliance_score, d.trust_score, d.last_seen_at "
                        + "FROM devices d WHERE d.tenant_id = ? ORDER BY d.trust_score DESC, d.last_seen_at DESC "
                        + "LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countDevices(String tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM devices WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null ? count : 0;
    }
}
