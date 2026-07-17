package com.sentinelhub.module.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Repository
public class SecurityTimelineJdbcRepository {

    private final JdbcTemplate jdbc;

    public SecurityTimelineJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset, String sourceFilter) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, source, created_at, title, actor_type, actor_id, resource, severity, detail FROM ("
                        + "SELECT id, 'audit' AS source, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                        + "action AS title, actor_type, actor_id, resource, NULL AS severity, CAST(detail AS CHAR) AS detail "
                        + "FROM audit_logs WHERE tenant_id = ? "
                        + "UNION ALL "
                        + "SELECT id, 'client_event' AS source, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                        + "event_type AS title, 'agent' AS actor_type, device_id AS actor_id, severity AS resource, "
                        + "severity, CAST(detail AS CHAR) AS detail "
                        + "FROM client_events WHERE tenant_id = ?"
                        + ") timeline");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(tenantId);
        if (sourceFilter != null && !sourceFilter.isBlank()) {
            sql.append(" WHERE source = ?");
            args.add(sourceFilter);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public int count(String tenantId, String sourceFilter) {
        StringBuilder sql = new StringBuilder(
                "SELECT COUNT(*) FROM ("
                        + "SELECT 'audit' AS source FROM audit_logs WHERE tenant_id = ? "
                        + "UNION ALL "
                        + "SELECT 'client_event' AS source FROM client_events WHERE tenant_id = ?"
                        + ") timeline");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(tenantId);
        if (sourceFilter != null && !sourceFilter.isBlank()) {
            sql.append(" WHERE source = ?");
            args.add(sourceFilter);
        }
        Integer c = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return c != null ? c : 0;
    }
}
