package com.sentinelhub.module.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class AuditRepository {

    private final JdbcTemplate jdbc;

    public AuditRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void insert(String tenantId, String actorType, String actorId, String action,
                       String resource, String resourceId, String detailJson, String ip) {
        jdbc.update(
                "INSERT INTO audit_logs (id, tenant_id, actor_type, actor_id, action, resource, resource_id, detail, ip_address) "
                        + "VALUES (?,?,?,?,?,?,?,CAST(? AS JSON),?)",
                UUID.randomUUID().toString(), tenantId, actorType, actorId, action, resource, resourceId, detailJson, ip);
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT id, actor_type, actor_id, action, resource, resource_id, detail, ip_address, created_at "
                        + "FROM audit_logs WHERE tenant_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int count(String tenantId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM audit_logs WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null ? c : 0;
    }
}
