package com.sentinelhub.module.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AuditClickHouseProperties;
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

@Repository
public class ClickHouseSecurityTimelineRepository {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseSecurityTimelineRepository.class);

    private final AuditClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public ClickHouseSecurityTimelineRepository(AuditClickHouseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset, String sourceFilter) {
        if (!properties.enabled()) {
            return List.of();
        }
        String tenant = escapeSql(tenantId);
        StringBuilder sql = new StringBuilder(
                "SELECT id, source, created_at, title, actor_type, actor_id, resource, severity, detail "
                        + "FROM ("
                        + "SELECT id, 'audit' AS source, "
                        + "formatDateTime(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                        + "action AS title, actor_type, actor_id, resource, "
                        + "NULL AS severity, detail "
                        + "FROM " + properties.database() + ".audit_logs WHERE tenant_id = '" + tenant + "' "
                        + "UNION ALL "
                        + "SELECT id, 'client_event' AS source, "
                        + "formatDateTime(created_at, '%Y-%m-%d %H:%i:%s') AS created_at, "
                        + "event_type AS title, 'agent' AS actor_type, device_id AS actor_id, "
                        + "severity AS resource, severity, detail "
                        + "FROM " + properties.database() + ".client_events WHERE tenant_id = '" + tenant + "'"
                        + ") timeline");
        if (sourceFilter != null && !sourceFilter.isBlank()) {
            sql.append(" WHERE source = '").append(escapeSql(sourceFilter)).append("'");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ").append(limit)
                .append(" OFFSET ").append(offset)
                .append(" FORMAT JSONEachRow");

        try {
            return parseJsonEachRow(postQuery(sql.toString(), ""));
        } catch (Exception e) {
            log.warn("ClickHouse timeline list failed: {}", e.getMessage());
            return List.of();
        }
    }

    public int count(String tenantId, String sourceFilter) {
        if (!properties.enabled()) {
            return 0;
        }
        String tenant = escapeSql(tenantId);
        StringBuilder sql = new StringBuilder(
                "SELECT count() AS c FROM ("
                        + "SELECT 'audit' AS source FROM " + properties.database() + ".audit_logs WHERE tenant_id = '"
                        + tenant + "' "
                        + "UNION ALL "
                        + "SELECT 'client_event' AS source FROM " + properties.database() + ".client_events WHERE tenant_id = '"
                        + tenant + "'"
                        + ") timeline");
        if (sourceFilter != null && !sourceFilter.isBlank()) {
            sql.append(" WHERE source = '").append(escapeSql(sourceFilter)).append("'");
        }
        sql.append(" FORMAT JSONEachRow");

        try {
            List<Map<String, Object>> rows = parseJsonEachRow(postQuery(sql.toString(), ""));
            if (rows.isEmpty()) {
                return 0;
            }
            Object c = rows.getFirst().get("c");
            return c instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(c));
        } catch (Exception e) {
            log.warn("ClickHouse timeline count failed: {}", e.getMessage());
            return 0;
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
            Map<String, Object> row = objectMapper.readValue(line, Map.class);
            rows.add(new LinkedHashMap<>(row));
        }
        return rows;
    }

    private static String escapeSql(String value) {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }
}
