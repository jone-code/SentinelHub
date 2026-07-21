package com.sentinelhub.clickhouse;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

@Repository
public class ClickHouseMigrationLockRepository {

    public record LockRow(String lockName, String holderId, Instant acquiredAt, Instant expiresAt) {
    }

    private static final String LOCK_NAME = "schema_migration";

    private final JdbcTemplate jdbc;

    public ClickHouseMigrationLockRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void deleteExpired() {
        jdbc.update("DELETE FROM clickhouse_migration_locks WHERE expires_at < ?", Timestamp.from(Instant.now()));
    }

    public Optional<LockRow> find() {
        var rows = jdbc.query(
                "SELECT lock_name, holder_id, acquired_at, expires_at FROM clickhouse_migration_locks WHERE lock_name = ?",
                (rs, rowNum) -> new LockRow(
                        rs.getString("lock_name"),
                        rs.getString("holder_id"),
                        rs.getTimestamp("acquired_at").toInstant(),
                        rs.getTimestamp("expires_at").toInstant()),
                LOCK_NAME);
        return rows.stream().findFirst();
    }

    public boolean insert(String holderId, int ttlSeconds) {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(ttlSeconds);
        try {
            jdbc.update(
                    "INSERT INTO clickhouse_migration_locks (lock_name, holder_id, acquired_at, expires_at) VALUES (?,?,?,?)",
                    LOCK_NAME, holderId, Timestamp.from(now), Timestamp.from(expires));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public void renew(String holderId, int ttlSeconds) {
        jdbc.update(
                "UPDATE clickhouse_migration_locks SET expires_at = ? WHERE lock_name = ? AND holder_id = ?",
                Timestamp.from(Instant.now().plusSeconds(ttlSeconds)), LOCK_NAME, holderId);
    }

    public void release(String holderId) {
        jdbc.update("DELETE FROM clickhouse_migration_locks WHERE lock_name = ? AND holder_id = ?", LOCK_NAME, holderId);
    }

    public static String lockName() {
        return LOCK_NAME;
    }
}
