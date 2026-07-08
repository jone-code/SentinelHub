package com.sentinelhub.module.policy;

import com.sentinelhub.module.policy.domain.Policy;
import com.sentinelhub.module.policy.domain.PolicyBundle;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class PolicyRepository {

    private static final RowMapper<Policy> POLICY_MAPPER = (rs, rowNum) -> new Policy(
            rs.getString("id"),
            rs.getString("tenant_id"),
            rs.getString("name"),
            rs.getString("type"),
            rs.getString("status"),
            rs.getInt("priority"),
            rs.getString("scope"),
            rs.getString("content"),
            rs.getString("created_by"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
    );

    private final JdbcTemplate jdbc;

    public PolicyRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<Policy> listByTenant(String tenantId) {
        return jdbc.query(
                "SELECT id, tenant_id, name, type, status, priority, CAST(scope AS CHAR) AS scope, "
                        + "CAST(content AS CHAR) AS content, created_by, created_at, updated_at "
                        + "FROM policies WHERE tenant_id = ? ORDER BY priority ASC, updated_at DESC",
                POLICY_MAPPER, tenantId);
    }

    public Optional<Policy> findById(String tenantId, String id) {
        var list = jdbc.query(
                "SELECT id, tenant_id, name, type, status, priority, CAST(scope AS CHAR) AS scope, "
                        + "CAST(content AS CHAR) AS content, created_by, created_at, updated_at "
                        + "FROM policies WHERE tenant_id = ? AND id = ?",
                POLICY_MAPPER, tenantId, id);
        return list.stream().findFirst();
    }

    public Policy insert(String tenantId, String name, String type, String contentJson,
                         String scopeJson, int priority, String createdBy) {
        String id = UUID.randomUUID().toString();
        jdbc.update(
                "INSERT INTO policies (id, tenant_id, name, type, status, priority, scope, content, created_by) "
                        + "VALUES (?,?,?,?,'draft',?,CAST(? AS JSON),CAST(? AS JSON),?)",
                id, tenantId, name, type, priority, scopeJson, contentJson, createdBy);
        return findById(tenantId, id).orElseThrow();
    }

    public void updateDraft(String tenantId, String id, String name, String contentJson,
                            String scopeJson, int priority) {
        jdbc.update(
                "UPDATE policies SET name = ?, content = CAST(? AS JSON), scope = CAST(? AS JSON), "
                        + "priority = ?, updated_at = CURRENT_TIMESTAMP(3) "
                        + "WHERE tenant_id = ? AND id = ? AND status = 'draft'",
                name, contentJson, scopeJson, priority, tenantId, id);
    }

    public void markPublished(String tenantId, String id) {
        jdbc.update(
                "UPDATE policies SET status = 'published', updated_at = CURRENT_TIMESTAMP(3) WHERE tenant_id = ? AND id = ?",
                tenantId, id);
    }

    public int nextVersion(String policyId) {
        Integer max = jdbc.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM policy_versions WHERE policy_id = ?", Integer.class, policyId);
        return (max != null ? max : 0) + 1;
    }

    public void insertVersion(String tenantId, String policyId, int version, String contentJson, String publishedBy) {
        jdbc.update(
                "INSERT INTO policy_versions (id, tenant_id, policy_id, version, content, published_by) "
                        + "VALUES (?,?,?,?,CAST(? AS JSON),?)",
                UUID.randomUUID().toString(), tenantId, policyId, version, contentJson, publishedBy);
    }

    public List<Policy> listPublished(String tenantId) {
        return jdbc.query(
                "SELECT id, tenant_id, name, type, status, priority, CAST(scope AS CHAR) AS scope, "
                        + "CAST(content AS CHAR) AS content, created_by, created_at, updated_at "
                        + "FROM policies WHERE tenant_id = ? AND status = 'published' ORDER BY priority ASC",
                POLICY_MAPPER, tenantId);
    }

    public void upsertBundle(String tenantId, String version, String contentJson, String hash) {
        jdbc.update(
                "INSERT INTO tenant_policy_bundles (tenant_id, version, content, content_hash) VALUES (?,?,CAST(? AS JSON),?) "
                        + "ON DUPLICATE KEY UPDATE version = VALUES(version), content = VALUES(content), "
                        + "content_hash = VALUES(content_hash), updated_at = CURRENT_TIMESTAMP(3)",
                tenantId, version, contentJson, hash);
    }

    public Optional<PolicyBundle> findBundle(String tenantId) {
        var list = jdbc.query(
                "SELECT tenant_id, version, CAST(content AS CHAR) AS content, content_hash, published_at "
                        + "FROM tenant_policy_bundles WHERE tenant_id = ?",
                (rs, rowNum) -> new PolicyBundle(
                        rs.getString("tenant_id"),
                        rs.getString("version"),
                        rs.getString("content"),
                        rs.getString("content_hash"),
                        rs.getTimestamp("published_at").toInstant()
                ), tenantId);
        return list.stream().findFirst();
    }
}
