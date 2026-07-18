package com.sentinelhub.clickhouse;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ClickHouseMigrationStatus {

    public enum OverallStatus {
        IDLE, RUNNING, COMPLETED, FAILED, SKIPPED
    }

    public enum TableStatus {
        PENDING, SKIPPED, RUNNING, DONE, FAILED
    }

    public record TableMigration(
            String table,
            String engineBefore,
            TableStatus status,
            String step,
            String message,
            Long rowsCopied,
            Long totalRows,
            Instant startedAt,
            Instant finishedAt
    ) {
    }

    private volatile OverallStatus overallStatus = OverallStatus.IDLE;
    private volatile String overallMessage = "not started";
    private volatile Instant startedAt;
    private volatile Instant finishedAt;
    private final List<TableMigration> tables = new ArrayList<>();

    public synchronized void reset(String message) {
        overallStatus = OverallStatus.RUNNING;
        overallMessage = message;
        startedAt = Instant.now();
        finishedAt = null;
        tables.clear();
    }

    public synchronized void addTable(TableMigration table) {
        tables.add(table);
    }

    public synchronized void updateTable(String tableName, TableMigration updated) {
        for (int i = 0; i < tables.size(); i++) {
            if (tables.get(i).table().equals(tableName)) {
                tables.set(i, updated);
                return;
            }
        }
        tables.add(updated);
    }

    public synchronized void complete(String message) {
        overallStatus = OverallStatus.COMPLETED;
        overallMessage = message;
        finishedAt = Instant.now();
    }

    public synchronized void fail(String message) {
        overallStatus = OverallStatus.FAILED;
        overallMessage = message;
        finishedAt = Instant.now();
    }

    public synchronized void skip(String message) {
        overallStatus = OverallStatus.SKIPPED;
        overallMessage = message;
        finishedAt = Instant.now();
    }

    public synchronized void idle(String message) {
        overallStatus = OverallStatus.IDLE;
        overallMessage = message;
    }

    public synchronized boolean isRunning() {
        return overallStatus == OverallStatus.RUNNING;
    }

    public synchronized Map<String, Object> snapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("status", overallStatus.name().toLowerCase());
        out.put("message", overallMessage);
        out.put("started_at", startedAt != null ? startedAt.toString() : null);
        out.put("finished_at", finishedAt != null ? finishedAt.toString() : null);
        List<Map<String, Object>> tableRows = new ArrayList<>();
        for (TableMigration table : tables) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("table", table.table());
            row.put("engine_before", table.engineBefore());
            row.put("status", table.status().name().toLowerCase());
            row.put("step", table.step());
            row.put("message", table.message());
            row.put("rows_copied", table.rowsCopied());
            row.put("total_rows", table.totalRows());
            if (table.rowsCopied() != null && table.totalRows() != null && table.totalRows() > 0) {
                row.put("progress_percent", Math.min(100, table.rowsCopied() * 100 / table.totalRows()));
            } else {
                row.put("progress_percent", null);
            }
            row.put("started_at", table.startedAt() != null ? table.startedAt().toString() : null);
            row.put("finished_at", table.finishedAt() != null ? table.finishedAt().toString() : null);
            tableRows.add(row);
        }
        out.put("tables", tableRows);
        return out;
    }
}
