package com.sentinelhub.module.software;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ClientEventRepository {

    private final JdbcTemplate jdbc;

    public ClientEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String tenantId, String deviceId, String eventType, String severity, String detailJson) {
        jdbc.update(
                "INSERT INTO client_events (id, tenant_id, device_id, event_type, severity, detail) "
                        + "VALUES (?,?,?,?,?,CAST(? AS JSON))",
                UUID.randomUUID().toString(), tenantId, deviceId, eventType, severity, detailJson);
    }

    public List<Map<String, Object>> listByTenant(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT e.id, e.event_type, e.severity, e.detail, e.created_at, d.hostname, d.agent_id "
                        + "FROM client_events e JOIN devices d ON d.id = e.device_id "
                        + "WHERE e.tenant_id = ? ORDER BY e.created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countByTenant(String tenantId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM client_events WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null ? c : 0;
    }

    public int countOpenAlerts(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_events WHERE tenant_id = ? AND created_at >= NOW() - INTERVAL 24 HOUR",
                Integer.class, tenantId);
        return c != null ? c : 0;
    }

    public int countRecentHighSeverity(String deviceId, int hours) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_events WHERE device_id = ? AND created_at >= NOW() - INTERVAL ? HOUR "
                        + "AND severity IN ('high', 'critical')",
                Integer.class, deviceId, hours);
        return c != null ? c : 0;
    }

    public int countRecentWarnings(String deviceId, int hours) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_events WHERE device_id = ? AND created_at >= NOW() - INTERVAL ? HOUR "
                        + "AND severity = 'warning'",
                Integer.class, deviceId, hours);
        return c != null ? c : 0;
    }
}
