package com.sentinelhub.module.dlp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DlpRepository {

    private final JdbcTemplate jdbc;

    public DlpRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insert(String tenantId, String name, String channel, String action,
                         String patternsJson, boolean enabled, int priority) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO dlp_rules (id, tenant_id, name, channel, action, patterns, enabled, priority) "
                        + "VALUES (?,?,?,?,?,CAST(? AS JSON),?,?)",
                id, tenantId, name, channel, action, patternsJson, enabled ? 1 : 0, priority);
        return id;
    }

    public void update(String tenantId, String id, String name, String channel, String action,
                       String patternsJson, boolean enabled, int priority) {
        jdbc.update(
                "UPDATE dlp_rules SET name = ?, channel = ?, action = ?, patterns = CAST(? AS JSON), "
                        + "enabled = ?, priority = ?, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE tenant_id = ? AND id = ?",
                name, channel, action, patternsJson, enabled ? 1 : 0, priority, tenantId, id);
    }

    public Optional<Map<String, Object>> findById(String tenantId, String id) {
        var list = jdbc.queryForList(
                "SELECT id, name, channel, action, CAST(patterns AS CHAR) AS patterns, enabled, priority, "
                        + "created_at, updated_at FROM dlp_rules WHERE tenant_id = ? AND id = ?",
                tenantId, id);
        return list.stream().findFirst();
    }

    public List<Map<String, Object>> listByTenant(String tenantId) {
        return jdbc.queryForList(
                "SELECT id, name, channel, action, CAST(patterns AS CHAR) AS patterns, enabled, priority, "
                        + "created_at, updated_at FROM dlp_rules WHERE tenant_id = ? ORDER BY priority ASC, name",
                tenantId);
    }

    public List<Map<String, Object>> listEnabled(String tenantId) {
        return jdbc.queryForList(
                "SELECT id, name, channel, action, CAST(patterns AS CHAR) AS patterns, priority "
                        + "FROM dlp_rules WHERE tenant_id = ? AND enabled = 1 ORDER BY priority ASC",
                tenantId);
    }

    public boolean hasRules(String tenantId) {
        Integer c = jdbc.queryForObject("SELECT COUNT(*) FROM dlp_rules WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null && c > 0;
    }

    public List<Map<String, Object>> listEvents(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT e.id, e.event_type, e.severity, e.detail, e.created_at, d.hostname, d.agent_id "
                        + "FROM client_events e JOIN devices d ON d.id = e.device_id "
                        + "WHERE e.tenant_id = ? AND e.event_type LIKE 'dlp.%' "
                        + "ORDER BY e.created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countEvents(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM client_events WHERE tenant_id = ? AND event_type LIKE 'dlp.%'",
                Integer.class, tenantId);
        return c != null ? c : 0;
    }
}
