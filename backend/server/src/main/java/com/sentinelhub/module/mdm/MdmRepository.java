package com.sentinelhub.module.mdm;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class MdmRepository {

    private final JdbcTemplate jdbc;

    public MdmRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean hasProfiles(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mdm_profiles WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null && count > 0;
    }

    public String insertProfile(String tenantId, String name, String profileType, String contentJson, boolean enabled) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO mdm_profiles (id, tenant_id, name, profile_type, content, enabled) VALUES (?,?,?,?,CAST(? AS JSON),?)",
                id, tenantId, name, profileType, contentJson, enabled ? 1 : 0);
        return id;
    }

    public List<Map<String, Object>> listProfiles(String tenantId) {
        return jdbc.queryForList(
                "SELECT * FROM mdm_profiles WHERE tenant_id = ? ORDER BY name", tenantId);
    }

    public Optional<Map<String, Object>> findProfile(String tenantId, String id) {
        var list = jdbc.queryForList(
                "SELECT * FROM mdm_profiles WHERE tenant_id = ? AND id = ?", tenantId, id);
        return list.stream().findFirst();
    }

    public void updateProfile(String tenantId, String id, String name, String profileType,
                              String contentJson, boolean enabled) {
        jdbc.update(
                "UPDATE mdm_profiles SET name=?, profile_type=?, content=CAST(? AS JSON), enabled=? "
                        + "WHERE tenant_id=? AND id=?",
                name, profileType, contentJson, enabled ? 1 : 0, tenantId, id);
    }

    public void assignProfile(String tenantId, String deviceId, String profileId) {
        jdbc.update(
                "INSERT INTO mdm_device_assignments (device_id, profile_id, tenant_id, status) VALUES (?,?,?,'pending') "
                        + "ON DUPLICATE KEY UPDATE status='pending', assigned_at=CURRENT_TIMESTAMP(3), applied_at=NULL",
                deviceId, profileId, tenantId);
    }

    public void markApplied(String tenantId, String deviceId, String profileId) {
        jdbc.update(
                "UPDATE mdm_device_assignments SET status='applied', applied_at=CURRENT_TIMESTAMP(3) "
                        + "WHERE tenant_id=? AND device_id=? AND profile_id=?",
                tenantId, deviceId, profileId);
    }

    public List<Map<String, Object>> listAssignmentsForDevice(String tenantId, String deviceId) {
        return jdbc.queryForList(
                "SELECT a.device_id, a.profile_id, a.status, a.assigned_at, a.applied_at, "
                        + "p.name, p.profile_type, p.content, p.enabled "
                        + "FROM mdm_device_assignments a "
                        + "JOIN mdm_profiles p ON p.id = a.profile_id "
                        + "WHERE a.tenant_id = ? AND a.device_id = ? AND p.enabled = 1 "
                        + "ORDER BY a.assigned_at DESC",
                tenantId, deviceId);
    }

    public List<Map<String, Object>> listAssignmentsForAdmin(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT a.device_id, a.profile_id, a.status, a.assigned_at, a.applied_at, "
                        + "p.name AS profile_name, p.profile_type, d.hostname, d.agent_id "
                        + "FROM mdm_device_assignments a "
                        + "JOIN mdm_profiles p ON p.id = a.profile_id "
                        + "JOIN devices d ON d.id = a.device_id "
                        + "WHERE a.tenant_id = ? ORDER BY a.assigned_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countAssignments(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM mdm_device_assignments WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null ? count : 0;
    }

    public List<Map<String, Object>> listEnabledProfilesForTenant(String tenantId) {
        return jdbc.queryForList(
                "SELECT id, name, profile_type, content, updated_at FROM mdm_profiles "
                        + "WHERE tenant_id = ? AND enabled = 1 ORDER BY name",
                tenantId);
    }

    public Optional<Instant> latestProfileUpdate(String tenantId) {
        var list = jdbc.queryForList(
                "SELECT MAX(updated_at) AS updated_at FROM mdm_profiles WHERE tenant_id = ? AND enabled = 1",
                tenantId);
        if (list.isEmpty() || list.getFirst().get("updated_at") == null) {
            return Optional.empty();
        }
        return Optional.of(((java.sql.Timestamp) list.getFirst().get("updated_at")).toInstant());
    }
}
