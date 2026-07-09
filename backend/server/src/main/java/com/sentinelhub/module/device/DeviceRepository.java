package com.sentinelhub.module.device;

import com.sentinelhub.module.device.domain.Device;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DeviceRepository {

    private static final RowMapper<Device> ROW_MAPPER = (rs, rowNum) -> new Device(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("org_unit_id"),
            rs.getString("agent_id"),
            rs.getString("hostname"),
            rs.getString("os_type"),
            rs.getString("os_version"),
            rs.getString("hardware_id"),
            rs.getString("status"),
            toInstant(rs.getTimestamp("last_seen_at")),
            rs.getObject("compliance_score") != null ? rs.getInt("compliance_score") : null,
            rs.getObject("trust_score") != null ? rs.getInt("trust_score") : null,
            rs.getTimestamp("created_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public DeviceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Device> findByAgentId(String tenantId, String agentId) {
        var list = jdbc.query(
                "SELECT * FROM devices WHERE tenant_id = ? AND agent_id = ?",
                ROW_MAPPER, tenantId, agentId);
        return list.stream().findFirst();
    }

    public Optional<Device> findByAgentIdAny(String agentId) {
        var list = jdbc.query("SELECT * FROM devices WHERE agent_id = ? LIMIT 1", ROW_MAPPER, agentId);
        return list.stream().findFirst();
    }

    public Optional<Device> findById(String tenantId, String id) {
        var list = jdbc.query("SELECT * FROM devices WHERE tenant_id = ? AND id = ?", ROW_MAPPER, tenantId, id);
        return list.stream().findFirst();
    }

    public List<Device> listByTenant(String tenantId, int limit, int offset) {
        return jdbc.query(
                "SELECT * FROM devices WHERE tenant_id = ? ORDER BY last_seen_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER, tenantId, limit, offset);
    }

    public int countByTenant(String tenantId) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM devices WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null ? count : 0;
    }

    public int countOnlineByTenant(String tenantId, Instant since) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM devices WHERE tenant_id = ? AND last_seen_at >= ?",
                Integer.class, tenantId, Timestamp.from(since));
        return count != null ? count : 0;
    }

    public Double averageComplianceScore(String tenantId) {
        Double avg = jdbc.queryForObject(
                "SELECT AVG(compliance_score) FROM devices WHERE tenant_id = ? AND compliance_score IS NOT NULL",
                Double.class, tenantId);
        return avg != null ? avg : 0.0;
    }

    public Device insert(String tenantId, String agentId, String hostname, String osType,
                         String osVersion, String hardwareId) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO devices (id, tenant_id, agent_id, hostname, os_type, os_version, hardware_id, status, last_seen_at) "
                        + "VALUES (?,?,?,?,?,?,?,'active',CURRENT_TIMESTAMP(3))",
                id, tenantId, agentId, hostname, osType, osVersion, hardwareId);
        return findById(tenantId, id).orElseThrow();
    }

    public void updateHeartbeat(String tenantId, String agentId, String hostname, String osType, String osVersion) {
        jdbc.update(
                "UPDATE devices SET last_seen_at = CURRENT_TIMESTAMP(3), status = 'active', "
                        + "hostname = COALESCE(?, hostname), os_type = COALESCE(?, os_type), os_version = COALESCE(?, os_version) "
                        + "WHERE tenant_id = ? AND agent_id = ?",
                hostname, osType, osVersion, tenantId, agentId);
    }

    public void updateComplianceScore(String tenantId, String deviceId, int score) {
        jdbc.update(
                "UPDATE devices SET compliance_score = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE tenant_id = ? AND id = ?",
                score, tenantId, deviceId);
    }

    public void updateComplianceScoreByAgentId(String tenantId, String agentId, int score) {
        jdbc.update(
                "UPDATE devices SET compliance_score = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE tenant_id = ? AND agent_id = ?",
                score, tenantId, agentId);
    }

    public void updateTrustScore(String tenantId, String deviceId, int score) {
        jdbc.update(
                "UPDATE devices SET trust_score = ?, updated_at = CURRENT_TIMESTAMP(3) WHERE tenant_id = ? AND id = ?",
                score, tenantId, deviceId);
    }

    public Double averageTrustScore(String tenantId) {
        Double avg = jdbc.queryForObject(
                "SELECT AVG(trust_score) FROM devices WHERE tenant_id = ? AND trust_score IS NOT NULL",
                Double.class, tenantId);
        return avg != null ? avg : 0.0;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
