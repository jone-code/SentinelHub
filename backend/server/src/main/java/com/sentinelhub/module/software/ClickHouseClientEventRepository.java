package com.sentinelhub.module.software;

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
import java.util.UUID;

@Repository
public class ClickHouseClientEventRepository {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseClientEventRepository.class);

    private final AuditClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public ClickHouseClientEventRepository(AuditClickHouseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public boolean isEnabled() {
        return properties.enabled();
    }

    public void insert(String tenantId, String deviceId, String eventType, String severity, String detailJson) {
        if (!properties.enabled()) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", UUID.randomUUID().toString());
        row.put("tenant_id", tenantId);
        row.put("device_id", deviceId);
        row.put("event_type", eventType);
        row.put("severity", severity);
        row.put("detail", detailJson);
        try {
            String body = objectMapper.writeValueAsString(row) + "\n";
            String query = "INSERT INTO " + properties.database() + ".client_events FORMAT JSONEachRow";
            postQuery(query, body);
        } catch (Exception e) {
            log.warn("ClickHouse client_events insert failed: {}", e.getMessage());
        }
    }

    public List<Map<String, Object>> list(String tenantId, int limit, int offset,
                                           String eventTypeFilter, String severityFilter) {
        if (!properties.enabled()) {
            return List.of();
        }
        StringBuilder sql = new StringBuilder(
                "SELECT id, device_id, event_type, severity, detail, "
                        + "formatDateTime(created_at, '%Y-%m-%d %H:%i:%s') AS created_at "
                        + "FROM " + properties.database() + ".client_events WHERE tenant_id = '"
                        + escapeSql(tenantId) + "'");
        if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
            sql.append(" AND event_type LIKE '%").append(escapeSql(eventTypeFilter)).append("%'");
        }
        if (severityFilter != null && !severityFilter.isBlank()) {
            sql.append(" AND severity = '").append(escapeSql(severityFilter)).append("'");
        }
        sql.append(" ORDER BY created_at DESC LIMIT ").append(limit)
                .append(" OFFSET ").append(offset).append(" FORMAT JSONEachRow");

        try {
            return parseJsonEachRow(postQuery(sql.toString(), ""));
        } catch (Exception e) {
            log.warn("ClickHouse client_events list failed: {}", e.getMessage());
            return List.of();
        }
    }

    public int count(String tenantId, String eventTypeFilter, String severityFilter) {
        if (!properties.enabled()) {
            return 0;
        }
        StringBuilder sql = new StringBuilder(
                "SELECT count() AS c FROM " + properties.database() + ".client_events WHERE tenant_id = '"
                        + escapeSql(tenantId) + "'");
        if (eventTypeFilter != null && !eventTypeFilter.isBlank()) {
            sql.append(" AND event_type LIKE '%").append(escapeSql(eventTypeFilter)).append("%'");
        }
        if (severityFilter != null && !severityFilter.isBlank()) {
            sql.append(" AND severity = '").append(escapeSql(severityFilter)).append("'");
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
            log.warn("ClickHouse client_events count failed: {}", e.getMessage());
            return 0;
        }
    }

    public void ensureSchema() {
        if (!properties.enabled()) {
            return;
        }
        try {
            postQuery("CREATE DATABASE IF NOT EXISTS " + properties.database(), "");
            postQuery(
                    "CREATE TABLE IF NOT EXISTS " + properties.database() + ".client_events ("
                            + "id String, tenant_id String, device_id String, "
                            + "event_type String, severity String, detail String, "
                            + "created_at DateTime64(3) DEFAULT now64(3)"
                            + ") ENGINE = MergeTree() ORDER BY (tenant_id, created_at) "
                            + "TTL created_at + INTERVAL 365 DAY", "");
        } catch (Exception e) {
            log.warn("ClickHouse client_events schema init failed: {}", e.getMessage());
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
