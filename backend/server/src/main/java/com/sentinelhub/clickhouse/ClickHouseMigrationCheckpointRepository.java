package com.sentinelhub.clickhouse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ClickHouseMigrationCheckpointRepository {

    public record Checkpoint(String tableName, long rowsCopied, Long totalRows, String step) {
    }

    private final JdbcTemplate jdbc;

    public ClickHouseMigrationCheckpointRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<Checkpoint> find(String tableName) {
        var rows = jdbc.query(
                "SELECT table_name, rows_copied, total_rows, step FROM clickhouse_migration_checkpoints WHERE table_name = ?",
                (rs, rowNum) -> new Checkpoint(
                        rs.getString("table_name"),
                        rs.getLong("rows_copied"),
                        rs.getObject("total_rows") != null ? rs.getLong("total_rows") : null,
                        rs.getString("step")),
                tableName);
        return rows.stream().findFirst();
    }

    public void upsert(String tableName, long rowsCopied, Long totalRows, String step) {
        jdbc.update(
                "INSERT INTO clickhouse_migration_checkpoints (table_name, rows_copied, total_rows, step) "
                        + "VALUES (?, ?, ?, ?) "
                        + "ON DUPLICATE KEY UPDATE rows_copied = VALUES(rows_copied), "
                        + "total_rows = VALUES(total_rows), step = VALUES(step)",
                tableName, rowsCopied, totalRows, step);
    }

    public void delete(String tableName) {
        jdbc.update("DELETE FROM clickhouse_migration_checkpoints WHERE table_name = ?", tableName);
    }
}
