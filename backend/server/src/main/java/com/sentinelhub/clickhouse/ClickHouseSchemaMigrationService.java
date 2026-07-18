package com.sentinelhub.clickhouse;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sentinelhub.config.AuditClickHouseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 将存量 MergeTree 表在线迁移为 ReplacingMergeTree（CREATE _new → INSERT → RENAME → DROP）。
 */
@Service
public class ClickHouseSchemaMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseSchemaMigrationService.class);

    private static final String AUDIT_LOGS = "audit_logs";
    private static final String CLIENT_EVENTS = "client_events";

    private final AuditClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final ClickHouseMigrationStatus status = new ClickHouseMigrationStatus();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public ClickHouseSchemaMigrationService(AuditClickHouseProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> snapshot() {
        return status.snapshot();
    }

    public void migrateToReplacingMergeTreeIfNeeded() {
        if (!properties.enabled() || !properties.replacingMergeMigrateOnStartup()) {
            status.idle("migration not scheduled on startup");
            return;
        }
        runMigration("startup");
    }

    public synchronized void runMigration(String trigger) {
        if (!properties.enabled()) {
            status.skip("ClickHouse disabled");
            return;
        }
        if (!properties.replacingMerge()) {
            status.skip("replacing-merge is false");
            return;
        }
        if (status.isRunning()) {
            throw new IllegalStateException("migration already running");
        }
        status.reset("trigger=" + trigger);
        try {
            migrateTable(AUDIT_LOGS);
            migrateTable(CLIENT_EVENTS);
            status.complete("migration finished");
        } catch (Exception e) {
            status.fail(e.getMessage());
            throw new IllegalStateException("ClickHouse ReplacingMergeTree migration failed", e);
        }
    }

    private void migrateTable(String table) throws Exception {
        Instant started = Instant.now();
        String engine = readEngine(table);
        if (engine == null) {
            log.info("ClickHouse table {} does not exist; skipping migration", table);
            status.addTable(new ClickHouseMigrationStatus.TableMigration(
                    table, null, ClickHouseMigrationStatus.TableStatus.SKIPPED,
                    "check", "table does not exist", started, Instant.now()));
            return;
        }
        if (engine.contains("ReplacingMergeTree")) {
            log.info("ClickHouse table {} already ReplacingMergeTree", table);
            status.addTable(new ClickHouseMigrationStatus.TableMigration(
                    table, engine, ClickHouseMigrationStatus.TableStatus.SKIPPED,
                    "check", "already ReplacingMergeTree", started, Instant.now()));
            return;
        }
        if (!engine.contains("MergeTree")) {
            log.warn("ClickHouse table {} has unexpected engine {}; skipping migration", table, engine);
            status.addTable(new ClickHouseMigrationStatus.TableMigration(
                    table, engine, ClickHouseMigrationStatus.TableStatus.SKIPPED,
                    "check", "unexpected engine: " + engine, started, Instant.now()));
            return;
        }

        status.addTable(new ClickHouseMigrationStatus.TableMigration(
                table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                "prepare", "starting migration", started, null));

        String db = properties.database();
        String newTable = table + "_new";
        String oldTable = table + "_old";
        log.info("Migrating ClickHouse {} from {} to ReplacingMergeTree", table, engine);

        updateStep(table, engine, "drop_staging", "dropping staging tables", started);
        postQuery("DROP TABLE IF EXISTS " + db + "." + newTable, "");
        postQuery("DROP TABLE IF EXISTS " + db + "." + oldTable, "");

        updateStep(table, engine, "create_new", "creating ReplacingMergeTree table", started);
        postQuery(createReplacingTableSql(db, newTable, table), "");

        updateStep(table, engine, "copy_data", "copying rows", started);
        postQuery("INSERT INTO " + db + "." + newTable + " SELECT * FROM " + db + "." + table, "");

        updateStep(table, engine, "rename", "swapping tables", started);
        postQuery("RENAME TABLE " + db + "." + table + " TO " + db + "." + oldTable
                + ", " + db + "." + newTable + " TO " + db + "." + table, "");

        updateStep(table, engine, "drop_old", "dropping old table", started);
        postQuery("DROP TABLE IF EXISTS " + db + "." + oldTable, "");

        status.updateTable(table, new ClickHouseMigrationStatus.TableMigration(
                table, engine, ClickHouseMigrationStatus.TableStatus.DONE,
                "done", "migrated to ReplacingMergeTree", started, Instant.now()));
        log.info("ClickHouse table {} migrated to ReplacingMergeTree", table);
    }

    private void updateStep(String table, String engine, String step, String message, Instant started) {
        status.updateTable(table, new ClickHouseMigrationStatus.TableMigration(
                table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                step, message, started, null));
    }

    private String createReplacingTableSql(String db, String newTable, String sourceTable) {
        if (AUDIT_LOGS.equals(sourceTable)) {
            return "CREATE TABLE " + db + "." + newTable + " ("
                    + "id String, tenant_id String, actor_type String, actor_id String, "
                    + "action String, resource Nullable(String), resource_id Nullable(String), "
                    + "detail String, ip_address Nullable(String), "
                    + "created_at DateTime64(3) DEFAULT now64(3)"
                    + ") ENGINE = ReplacingMergeTree(created_at) ORDER BY (tenant_id, id)";
        }
        return "CREATE TABLE " + db + "." + newTable + " ("
                + "id String, tenant_id String, device_id String, "
                + "event_type String, severity String, detail String, "
                + "created_at DateTime64(3) DEFAULT now64(3)"
                + ") ENGINE = ReplacingMergeTree(created_at) ORDER BY (tenant_id, id)"
                + " TTL created_at + INTERVAL 365 DAY";
    }

    private String readEngine(String table) throws Exception {
        String sql = "SELECT engine FROM system.tables WHERE database = '"
                + escapeSql(properties.database()) + "' AND name = '" + escapeSql(table)
                + "' FORMAT JSONEachRow";
        String response = postQuery(sql, "");
        List<Map<String, Object>> rows = parseJsonEachRow(response);
        if (rows.isEmpty()) {
            return null;
        }
        Object engine = rows.getFirst().get("engine");
        return engine != null ? engine.toString() : null;
    }

    private String postQuery(String query, String body) throws Exception {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.url() + "/?query=" + encoded))
                .timeout(Duration.ofSeconds(120))
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
