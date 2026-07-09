package com.sentinelhub.module.ai;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AiRepository {

    private final JdbcTemplate jdbc;

    public AiRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String insertInsight(String tenantId, String insightType, String severity, String title,
                                String summary, String evidenceJson, String deviceId) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO ai_insights (id, tenant_id, insight_type, severity, title, summary, evidence, "
                        + "related_device_id) VALUES (?,?,?,?,?,?,CAST(? AS JSON),?)",
                id, tenantId, insightType, severity, title, summary, evidenceJson, deviceId);
        return id;
    }

    public void resolveOpenByType(String tenantId, String insightType) {
        jdbc.update(
                "UPDATE ai_insights SET status='resolved', updated_at=CURRENT_TIMESTAMP(3) "
                        + "WHERE tenant_id=? AND insight_type=? AND status='open'",
                tenantId, insightType);
    }

    public List<Map<String, Object>> listByTenant(String tenantId, String status, int limit, int offset) {
        if (status == null || status.isBlank()) {
            return jdbc.queryForList(
                    "SELECT i.*, d.hostname, d.agent_id FROM ai_insights i "
                            + "LEFT JOIN devices d ON d.id = i.related_device_id "
                            + "WHERE i.tenant_id = ? ORDER BY i.created_at DESC LIMIT ? OFFSET ?",
                    tenantId, limit, offset);
        }
        return jdbc.queryForList(
                "SELECT i.*, d.hostname, d.agent_id FROM ai_insights i "
                        + "LEFT JOIN devices d ON d.id = i.related_device_id "
                        + "WHERE i.tenant_id = ? AND i.status = ? ORDER BY i.created_at DESC LIMIT ? OFFSET ?",
                tenantId, status, limit, offset);
    }

    public int countByTenant(String tenantId, String status) {
        if (status == null || status.isBlank()) {
            Integer c = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM ai_insights WHERE tenant_id = ?", Integer.class, tenantId);
            return c != null ? c : 0;
        }
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM ai_insights WHERE tenant_id = ? AND status = ?",
                Integer.class, tenantId, status);
        return c != null ? c : 0;
    }

    public Optional<Map<String, Object>> findById(String tenantId, String id) {
        var list = jdbc.queryForList(
                "SELECT i.*, d.hostname, d.agent_id FROM ai_insights i "
                        + "LEFT JOIN devices d ON d.id = i.related_device_id "
                        + "WHERE i.tenant_id = ? AND i.id = ?",
                tenantId, id);
        return list.stream().findFirst();
    }

    public void updateStatus(String tenantId, String id, String status) {
        jdbc.update(
                "UPDATE ai_insights SET status=?, updated_at=CURRENT_TIMESTAMP(3) WHERE tenant_id=? AND id=?",
                status, tenantId, id);
    }

    public List<Map<String, Object>> countEventsByDevice(String tenantId, int hours) {
        return jdbc.queryForList(
                "SELECT e.device_id, d.hostname, d.agent_id, e.severity, COUNT(*) AS event_count "
                        + "FROM client_events e JOIN devices d ON d.id = e.device_id "
                        + "WHERE e.tenant_id = ? AND e.created_at >= NOW() - INTERVAL ? HOUR "
                        + "GROUP BY e.device_id, d.hostname, d.agent_id, e.severity "
                        + "HAVING event_count >= 3",
                tenantId, hours);
    }

    public List<Map<String, Object>> listLowComplianceDevices(String tenantId, int maxScore) {
        return jdbc.queryForList(
                "SELECT id, hostname, agent_id, compliance_score, trust_score FROM devices "
                        + "WHERE tenant_id = ? AND compliance_score IS NOT NULL AND compliance_score < ? "
                        + "ORDER BY compliance_score ASC LIMIT 20",
                tenantId, maxScore);
    }

    public int countNacRiskDevices(String tenantId) {
        Integer c = jdbc.queryForObject(
                "SELECT COUNT(*) FROM nac_device_status WHERE tenant_id = ? AND access_state IN ('denied','restricted')",
                Integer.class, tenantId);
        return c != null ? c : 0;
    }
}
