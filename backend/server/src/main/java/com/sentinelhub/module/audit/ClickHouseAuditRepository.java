package com.sentinelhub.module.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AuditClickHouseProperties;
import com.sentinelhub.config.ClickHouseTableNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Repository;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Repository
public class ClickHouseAuditRepository {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseAuditRepository.class);

    private final AuditClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public ClickHouseAuditRepository(AuditClickHouseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void insert(String id, String tenantId, String actorType, String actorId, String action,
                       String resource, String resourceId, String detailJson, String ip) {
        if (!properties.enabled()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", id);
        row.put("tenant_id", tenantId);
        row.put("actor_type", actorType);
        row.put("actor_id", actorId);
        row.put("action", action);
        row.put("resource", resource);
        row.put("resource_id", resourceId);
        row.put("detail", detailJson);
        row.put("ip_address", ip);
        try {
            String body = objectMapper.writeValueAsString(row) + "\n";
            String query = "INSERT INTO " + properties.database() + ".audit_logs FORMAT JSONEachRow";
            postQuery(query, body);
        } catch (Exception e) {
            log.warn("ClickHouse audit insert failed: {}", e.getMessage());
        }
    }

    public void insert(String tenantId, String actorType, String actorId, String action,
                       String resource, String resourceId, String detailJson, String ip) {
        insert(UUID.randomUUID().toString(), tenantId, actorType, actorId, action, resource, resourceId, detailJson, ip);
    }

    public void batchInsert(List<AuditRepository.AuditRow> rows) {
        if (!properties.enabled() || rows == null || rows.isEmpty()) {
            return;
        }
        try {
            StringBuilder body = new StringBuilder();
            for (AuditRepository.AuditRow row : rows) {
                Map<String, Object> map = new LinkedHashMap<>();
                map.put("id", row.id());
                map.put("tenant_id", row.tenantId());
                map.put("actor_type", row.actorType());
                map.put("actor_id", row.actorId());
                map.put("action", row.action());
                map.put("resource", row.resource());
                map.put("resource_id", row.resourceId());
                map.put("detail", row.detailJson());
                map.put("ip_address", row.ip());
                body.append(objectMapper.writeValueAsString(map)).append('\n');
            }
            String query = "INSERT INTO " + properties.database() + ".audit_logs FORMAT JSONEachRow";
            postQuery(query, body.toString());
        } catch (Exception e) {
            log.warn("ClickHouse audit batch insert failed: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset, String actionFilter) {
        if (!properties.enabled()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, actor_type, actor_id, action, resource, resource_id, detail, ip_address, "
                        + "formatDateTime(created_at, '%Y-%m-%d %H:%i:%s') AS created_at "
                        + "FROM " + ClickHouseTableNames.qualified(properties, "audit_logs") + " WHERE tenant_id = '"
                        + escapeSql(tenantId) + "'");
        if (actionFilter != null && !actionFilter.isBlank()) {
            sql.append(" AND action LIKE '%").append(escapeSql(actionFilter)).append("%'");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ").append(limit)
                .append(" OFFSET ").append(offset).append(" FORMAT JSONEachRow");

        try {
            String response = postQuery(sql.toString(), "");
            return parseJsonEachRow(response);
        } catch (Exception e) {
            log.warn("ClickHouse audit list failed: {}", e.getMessage());
            return List.of();
        }
    }

    public int count(String tenantId, String actionFilter) {
        if (!properties.enabled()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT count() AS c FROM " + ClickHouseTableNames.qualified(properties, "audit_logs") + " WHERE tenant_id = '"
                        + escapeSql(tenantId) + "'");
        if (actionFilter != null && !actionFilter.isBlank()) {
            sql.append(" AND action LIKE '%").append(escapeSql(actionFilter)).append("%'");
        }
        sql.append(" FORMAT JSONEachRow");

        try {
            String response = postQuery(sql.toString(), "");
            List<Map<String, Object>> rows = parseJsonEachRow(response);
            if (rows.isEmpty()) {
                return 0;
            }
            Object c = rows.getFirst().get("c");
            return c instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(c));
        } catch (Exception e) {
            log.warn("ClickHouse audit count failed: {}", e.getMessage());
            return 0;
        }
    }

    public void ensureSchema() {
        if (!properties.enabled()) {
            return;
        }
        try {
            postQuery("CREATE DATABASE IF NOT EXISTS " + properties.database(), "");
            String engine = properties.replacingMerge()
                    ? "ReplacingMergeTree(created_at) ORDER BY (tenant_id, id)"
                    : "MergeTree() ORDER BY (tenant_id, created_at)";
            postQuery(
                    "CREATE TABLE IF NOT EXISTS " + properties.database() + ".audit_logs ("
                            + "id String, tenant_id String, actor_type String, actor_id String, "
                            + "action String, resource Nullable(String), resource_id Nullable(String), "
                            + "detail String, ip_address Nullable(String), "
                            + "created_at DateTime64(3) DEFAULT now64(3)"
                            + ") ENGINE = " + engine, "");
        } catch (Exception e) {
            log.warn("ClickHouse schema init failed: {}", e.getMessage());
        }
    }

    private String postQuery(String query, String body) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.url() + "/?query=" + encoded))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("ClickHouse HTTP " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseJsonEachRow(String response) throws JsonProcessingException {
        if (response == null || response.isBlank()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (String line : response.split("\n")) {
            if (line.isBlank()) {
                continue;
            }
            rows.add(objectMapper.readValue(line, Map.class));
        }
        return rows;
    }

    private static String escapeSql(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
