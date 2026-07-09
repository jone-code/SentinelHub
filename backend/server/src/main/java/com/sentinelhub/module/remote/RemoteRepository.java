package com.sentinelhub.module.remote;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class RemoteRepository {

    private final JdbcTemplate jdbc;

    public RemoteRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insertSession(String tenantId, String deviceId, String operatorUserId, String operatorName,
                                String reason, boolean consentRequired) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO remote_sessions (id, tenant_id, device_id, operator_user_id, operator_name, reason, "
                        + "consent_required, status) VALUES (?,?,?,?,?,?,?,'pending')",
                id, tenantId, deviceId, operatorUserId, operatorName, reason, consentRequired ? 1 : 0);
        return id;
    }

    public Optional<Map<String, Object>> findById(String tenantId, String id) {
        var list = jdbc.queryForList(
                "SELECT s.*, d.hostname, d.agent_id FROM remote_sessions s "
                        + "JOIN devices d ON d.id = s.device_id "
                        + "WHERE s.tenant_id = ? AND s.id = ?",
                tenantId, id);
        return list.stream().findFirst();
    }

    public List<Map<String, Object>> listByTenant(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT s.*, d.hostname, d.agent_id FROM remote_sessions s "
                        + "JOIN devices d ON d.id = s.device_id "
                        + "WHERE s.tenant_id = ? ORDER BY s.created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countByTenant(String tenantId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM remote_sessions WHERE tenant_id = ?", Integer.class, tenantId);
        return count != null ? count : 0;
    }

    public List<Map<String, Object>> findPendingByDevice(String deviceId) {
        return jdbc.queryForList(
                "SELECT * FROM remote_sessions WHERE device_id = ? AND status = 'pending' ORDER BY created_at",
                deviceId);
    }

    public Optional<Map<String, Object>> findActiveByDevice(String deviceId) {
        var list = jdbc.queryForList(
                "SELECT * FROM remote_sessions WHERE device_id = ? AND status = 'active' ORDER BY started_at DESC LIMIT 1",
                deviceId);
        return list.stream().findFirst();
    }

    public void markConsented(String tenantId, String id, boolean accepted, Instant when) {
        if (accepted) {
            jdbc.update(
                    "UPDATE remote_sessions SET status='active', consented_at=?, started_at=?, updated_at=CURRENT_TIMESTAMP(3) "
                            + "WHERE tenant_id=? AND id=? AND status='pending'",
                    java.sql.Timestamp.from(when), java.sql.Timestamp.from(when), tenantId, id);
        } else {
            jdbc.update(
                    "UPDATE remote_sessions SET status='cancelled', ended_at=?, updated_at=CURRENT_TIMESTAMP(3) "
                            + "WHERE tenant_id=? AND id=? AND status='pending'",
                    java.sql.Timestamp.from(when), tenantId, id);
        }
    }

    public void endSession(String tenantId, String id, String recordingKey, Instant when) {
        jdbc.update(
                "UPDATE remote_sessions SET status='ended', ended_at=?, recording_key=COALESCE(?, recording_key), "
                        + "updated_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=? AND status IN ('pending','active')",
                java.sql.Timestamp.from(when), recordingKey, tenantId, id);
    }

    public void cancelSession(String tenantId, String id, Instant when) {
        jdbc.update(
                "UPDATE remote_sessions SET status='cancelled', ended_at=?, updated_at=CURRENT_TIMESTAMP(3) "
                        + "WHERE tenant_id=? AND id=? AND status IN ('pending','active')",
                java.sql.Timestamp.from(when), tenantId, id);
    }

    public void insertSignaling(String tenantId, String sessionId, String role, String sdpType, String sdpPayload) {
        jdbc.update(
                "INSERT INTO remote_signaling (id, tenant_id, session_id, role, sdp_type, sdp_payload) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID().toString(), tenantId, sessionId, role, sdpType, sdpPayload);
    }

    public Optional<Map<String, Object>> findLatestSignaling(String sessionId, String role) {
        var list = jdbc.queryForList(
                "SELECT * FROM remote_signaling WHERE session_id = ? AND role = ? ORDER BY created_at DESC LIMIT 1",
                sessionId, role);
        return list.stream().findFirst();
    }

    public List<Map<String, Object>> listSignaling(String tenantId, String sessionId) {
        return jdbc.queryForList(
                "SELECT role, sdp_type, sdp_payload, created_at FROM remote_signaling "
                        + "WHERE tenant_id = ? AND session_id = ? ORDER BY created_at",
                tenantId, sessionId);
    }
}
