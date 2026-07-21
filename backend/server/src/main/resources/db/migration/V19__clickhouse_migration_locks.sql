-- Distributed lock for ClickHouse migration across server instances
CREATE TABLE IF NOT EXISTS clickhouse_migration_locks (
    lock_name    VARCHAR(64)  PRIMARY KEY,
    holder_id    VARCHAR(128) NOT NULL,
    acquired_at  DATETIME(3)  NOT NULL,
    expires_at   DATETIME(3)  NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
