package com.sentinelhub.module.dlp;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class DlpEvidenceRepository {

    private final JdbcTemplate jdbc;

    public DlpEvidenceRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insert(String tenantId, String deviceId, String ruleId, String eventId,
                         String objectKey, String filename, String contentType, int sizeBytes,
                         String sha256, String channel) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO dlp_evidence (id, tenant_id, device_id, rule_id, event_id, object_key, filename, "
                        + "content_type, size_bytes, sha256, channel) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id, tenantId, deviceId, ruleId, eventId, objectKey, filename, contentType, sizeBytes, sha256, channel);
        return id;
    }

    public Optional<Map<String, Object>> findById(String tenantId, String id) {
        var list = jdbc.queryForList(
                "SELECT e.id, e.rule_id, e.event_id, e.object_key, e.filename, e.content_type, e.size_bytes, "
                        + "e.sha256, e.channel, e.created_at, d.hostname, d.agent_id "
                        + "FROM dlp_evidence e JOIN devices d ON d.id = e.device_id "
                        + "WHERE e.tenant_id = ? AND e.id = ?",
                tenantId, id);
        return list.stream().findFirst();
    }

    public List<Map<String, Object>> listByTenant(String tenantId, int limit, int offset) {
        return jdbc.queryForList(
                "SELECT e.id, e.rule_id, e.filename, e.size_bytes, e.sha256, e.channel, e.created_at, "
                        + "d.hostname, d.agent_id FROM dlp_evidence e "
                        + "JOIN devices d ON d.id = e.device_id "
                        + "WHERE e.tenant_id = ? ORDER BY e.created_at DESC LIMIT ? OFFSET ?",
                tenantId, limit, offset);
    }

    public int countByTenant(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dlp_evidence WHERE tenant_id = ?", Integer.class, tenantId);
        return c != null ? c : 0;
    }
}
