package com.sentinelhub.module.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String id, String tenantId, String actorType, String actorId, String action,
                       String resource, String resourceId, String detailJson, String ip) {
        jdbc.update(
                "INSERT INTO audit_logs (id, tenant_id, actor_type, actor_id, action, resource, resource_id, detail, ip_address) "
                        + "VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),?)",
                id, tenantId, actorType, actorId, action, resource, resourceId, detailJson, ip);
    }

    public void insert(String tenantId, String actorType, String actorId, String action,
                       String resource, String resourceId, String detailJson, String ip) {
        insert(UUID.randomUUID().toString(), tenantId, actorType, actorId, action, resource, resourceId, detailJson, ip);
    }

    public void batchInsert(List<AuditRow> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        jdbc.batchUpdate(
                "INSERT INTO audit_logs (id, tenant_id, actor_type, actor_id, action, resource, resource_id, detail, ip_address) "
                        + "VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),?)",
                rows,
                rows.size(),
                (ps, row) -> {
                    ps.setString(1, row.id());
                    ps.setString(2, row.tenantId());
                    ps.setString(3, row.actorType());
                    ps.setString(4, row.actorId());
                    ps.setString(5, row.action());
                    ps.setString(6, row.resource());
                    ps.setString(7, row.resourceId());
                    ps.setString(8, row.detailJson());
                    ps.setString(9, row.ip());
                });
    }

    public record AuditRow(
            String id,
            String tenantId,
            String actorType,
            String actorId,
            String action,
            String resource,
            String resourceId,
            String detailJson,
            String ip
    ) {}

    public List<Map<String, Object>> list(String tenantId, int limit, int offset, String actionFilter) {
        StringBuilder sql = new StringBuilder(
                "SELECT id, actor_type, actor_id, action, resource, resource_id, detail, ip_address, created_at "
                        + "FROM audit_logs WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (actionFilter != null && !actionFilter.isBlank()) {
            sql.append(" AND action LIKE ?");
            args.add("%" + actionFilter + "%");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ? OFFSET ?");
        args.add(limit);
        args.add(offset);
        return jdbc.queryForList(sql.toString(), args.toArray());
    }

    public int count(String tenantId, String actionFilter) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?");
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        if (actionFilter != null && !actionFilter.isBlank()) {
            sql.append(" AND action LIKE ?");
            args.add("%" + actionFilter + "%");
        }
        Integer c = jdbc.queryForObject(sql.toString(), Integer.class, args.toArray());
        return c != null ? c : 0;
    }

    public List<Map<String, Object>> listAfterWatermark(java.sql.Timestamp watermark, int limit) {
        return jdbc.queryForList(
                "SELECT id, tenant_id, actor_type, actor_id, action, resource, resource_id, "
                        + "CAST(detail AS CHAR) AS detail, ip_address, created_at "
                        + "FROM audit_logs WHERE created_at > ? ORDER BY created_at ASC LIMIT ?",
                watermark, limit);
    }
}
