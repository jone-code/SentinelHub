package com.sentinelhub.module.software;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ClientEventRepository {

    private final JdbcTemplate jdbc;

    public ClientEventRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String tenantId, String deviceId, String eventType, String severity, String detailJson) {
        jdbc.update(
                "INSERT INTO client_events (id, tenant_id, device_id, event_type, severity, detail) "
                        + "VALUES (?,?,?,?,?,CAST(? AS JSON))",
                id, tenantId, deviceId, eventType, severity, detailJson);
    }

    public void insert(String tenantId, String deviceId, String eventType, String severity, String detailJson) {
        insert(UUID.randomUUID().toString(), tenantId, deviceId, eventType, severity, detailJson);
    }

    public void batchInsert(List<ClientEventRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO client_events (id, tenant_id, device_id, event_type, severity, detail) "
                        + "VALUES (?,?,?,?,?,CAST(? AS JSON))",
                rows,
                rows.size(),
                (ps, row) -> {
                    ps.setString(1, row.id());
                    ps.setString(2, row.tenantId());
                    ps.setString(3, row.deviceId());
                    ps.setString(4, row.eventType());
                    ps.setString(5, row.severity());
                    ps.setString(6, row.detailJson());
                });
    }

    public record ClientEventRow(
            String id,
            String tenantId,
            String deviceId,
            String eventType,
            String severity,
            String detailJson
    ) {}

    public List<Map<String, Object>> listByTenant(String tenantId, int limit, int offset,
                                                   String eventTypeFilter, String severityFilter) {
        StringBuilder sql = new StringBuilder(
                "SELECT e.id, e.event_type, e.severity, e.detail, e.created_at, d.hostname, d.agent_id "
                        + "FROM client_events e JOIN devices d ON d.id = e.device_id "
                        + "WHERE e.tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
            sql.append(" AND e.event_type LIKE ?");
            args.add("%" + eventTypeFilter + "%");
        }
        if (severityFilter != null && !severityFilter.isBlank()) {
            sql.append(" AND e.severity = ?");
            args.add(severityFilter);
        }
        sql.append(" ORDER BY e.created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public int countByTenant(String tenantId, String eventTypeFilter, String severityFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM client_events WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
            sql.append(" AND event_type LIKE ?");
            args.add("%" + eventTypeFilter + "%");
        }
        if (severityFilter != null && !severityFilter.isBlank()) {
            sql.append(" AND severity = ?");
            args.add(severityFilter);
        }
        Integer c = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
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

    public List<Map<String, Object>> listAfterWatermark(java.sql.Timestamp watermark, int limit) {
        return jdbc.queryForList(
                "SELECT id, tenant_id, device_id, event_type, severity, CAST(detail AS CHAR) AS detail, created_at "
                        + "FROM client_events WHERE created_at > ? ORDER BY created_at ASC LIMIT ?",
                watermark, limit);
    }
}
