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
import java.util.Optional;

/**
 * 将存量 MergeTree 表在线迁移为 ReplacingMergeTree，支持分批复制与断点续传。
 */
@Service
public class ClickHouseSchemaMigrationService {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseSchemaMigrationService.class);

    private static final String AUDIT_LOGS = "audit_logs";
    private static final String CLIENT_EVENTS = "client_events";

    private final AuditClickHouseProperties properties;
    private final ObjectMapper objectMapper;
    private final ClickHouseMigrationCheckpointRepository checkpointRepository;
    private final ClickHouseMigrationLockService lockService;
    private final ClickHouseMigrationStatus status = new ClickHouseMigrationStatus();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    public ClickHouseSchemaMigrationService(AuditClickHouseProperties properties,
                                            ObjectMapper objectMapper,
                                            ClickHouseMigrationCheckpointRepository checkpointRepository,
                                            ClickHouseMigrationLockService lockService) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.checkpointRepository = checkpointRepository;
        this.lockService = lockService;
    }

    public Map<String, Object> snapshot() {
        return status.snapshot();
    }

    public boolean isRunning() {
        return status.isRunning();
    }

    public void migrateToReplacingMergeTreeIfNeeded() {
        if (!properties.enabled() || !properties.replacingMergeMigrateOnStartup()) {
            status.idle("migration not scheduled on startup");
        }
    }

    public synchronized void prepareAsyncRun(String trigger) {
        if (!properties.enabled()) {
            status.skip("ClickHouse disabled");
            throw new IllegalStateException("ClickHouse disabled");
        }
        if (!properties.replacingMerge()) {
            status.skip("replacing-merge is false");
            throw new IllegalStateException("replacing-merge is false");
        }
        if (status.isRunning()) {
            throw new IllegalStateException("migration already running");
        }
        status.reset("trigger=" + trigger);
    }

    public void runMigrationBody(String trigger) {
        try {
            migrateTable(AUDIT_LOGS);
            migrateTable(CLIENT_EVENTS);
            status.complete("migration finished");
        } catch (Exception e) {
            status.fail(e.getMessage());
            log.error("ClickHouse migration failed (trigger={}): {}", trigger, e.getMessage());
        }
    }

    public synchronized void runMigration(String trigger) {
        prepareAsyncRun(trigger);
        runMigrationBody(trigger);
    }

    private void migrateTable(String table) throws Exception {
        Instant started = Instant.now();
        String engine = readEngine(table);
        if (engine == null) {
            log.info("ClickHouse table {} does not exist; skipping migration", table);
            checkpointRepository.delete(table);
            status.addTable(skipped(table, null, "table does not exist", started));
            return;
        }
        if (engine.contains("ReplacingMergeTree")) {
            log.info("ClickHouse table {} already ReplacingMergeTree", table);
            checkpointRepository.delete(table);
            status.addTable(skipped(table, engine, "already ReplacingMergeTree", started));
            return;
        }
        if (!engine.contains("MergeTree")) {
            log.warn("ClickHouse table {} has unexpected engine {}; skipping migration", table, engine);
            status.addTable(skipped(table, engine, "unexpected engine: " + engine, started));
            return;
        }

        String db = properties.database();
        String newTable = table + "_new";
        String oldTable = table + "_old";

        Optional<ClickHouseMigrationCheckpointRepository.Checkpoint> checkpoint =
                properties.migrationResumeEnabled() ? checkpointRepository.find(table) : Optional.empty();
        boolean resumeCopy = checkpoint.isPresent() && "copy_data".equals(checkpoint.get().step())
                && tableExists(newTable);

        if (!resumeCopy) {
            updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                    "prepare", "starting migration", 0L, null, started);
            log.info("Migrating ClickHouse {} from {} to ReplacingMergeTree", table, engine);

            updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                    "drop_staging", "dropping staging tables", 0L, null, started);
            postQuery("DROP TABLE IF EXISTS " + db + "." + newTable, "");
            postQuery("DROP TABLE IF EXISTS " + db + "." + oldTable, "");
            checkpointRepository.delete(table);

            updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                    "create_new", "creating ReplacingMergeTree table", 0L, null, started);
            postQuery(createReplacingTableSql(db, newTable, table), "");
        } else {
            log.info("Resuming ClickHouse {} migration from offset {}", table, checkpoint.get().rowsCopied());
            updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                    "copy_data", "resuming batch copy", checkpoint.get().rowsCopied(),
                    checkpoint.get().totalRows(), started);
        }

        long totalRows = countRows(table);
        long offset = resumeCopy ? checkpoint.get().rowsCopied() : 0L;
        int batchSize = properties.migrationBatchSize();
        checkpointRepository.upsert(table, offset, totalRows, "copy_data");

        while (offset < totalRows) {
            lockService.renew();
            updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                    "copy_data", "copying rows " + offset + "/" + totalRows, offset, totalRows, started);
            String copySql = "INSERT INTO " + db + "." + newTable
                    + " SELECT * FROM " + db + "." + table
                    + " LIMIT " + batchSize + " OFFSET " + offset;
            postQuery(copySql, "");
            offset = Math.min(offset + batchSize, totalRows);
            checkpointRepository.upsert(table, offset, totalRows, "copy_data");
        }

        updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                "rename", "swapping tables", totalRows, totalRows, started);
        postQuery("RENAME TABLE " + db + "." + table + " TO " + db + "." + oldTable
                + ", " + db + "." + newTable + " TO " + db + "." + table, "");

        updateProgress(table, engine, ClickHouseMigrationStatus.TableStatus.RUNNING,
                "drop_old", "dropping old table", totalRows, totalRows, started);
        postQuery("DROP TABLE IF EXISTS " + db + "." + oldTable, "");

        checkpointRepository.delete(table);
        status.updateTable(table, new ClickHouseMigrationStatus.TableMigration(
                table, engine, ClickHouseMigrationStatus.TableStatus.DONE,
                "done", "migrated to ReplacingMergeTree", totalRows, totalRows, started, Instant.now()));
        log.info("ClickHouse table {} migrated to ReplacingMergeTree ({} rows)", table, totalRows);
    }

    private ClickHouseMigrationStatus.TableMigration skipped(String table, String engine, String message, Instant started) {
        return new ClickHouseMigrationStatus.TableMigration(
                table, engine, ClickHouseMigrationStatus.TableStatus.SKIPPED,
                "check", message, null, null, started, Instant.now());
    }

    private void updateProgress(String table, String engine, ClickHouseMigrationStatus.TableStatus tableStatus,
                                String step, String message, Long rowsCopied, Long totalRows, Instant started) {
        status.updateTable(table, new ClickHouseMigrationStatus.TableMigration(
                table, engine, tableStatus, step, message, rowsCopied, totalRows, started, null));
    }

    private boolean tableExists(String table) throws Exception {
        return readEngine(table) != null;
    }

    private long countRows(String table) throws Exception {
        String sql = "SELECT count() AS c FROM " + properties.database() + "." + table + " FORMAT JSONEachRow";
        List<Map<String, Object>> rows = parseJsonEachRow(postQuery(sql, ""));
        if (rows.isEmpty()) {
            return 0L;
        }
        Object c = rows.getFirst().get("c");
        return c instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(c));
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
                .timeout(Duration.ofSeconds(300))
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
