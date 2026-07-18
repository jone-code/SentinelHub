-- ClickHouse migration batch copy checkpoints (resume support)
CREATE TABLE IF NOT EXISTS clickhouse_migration_checkpoints (
    table_name   VARCHAR(64)  PRIMARY KEY,
    rows_copied  BIGINT       NOT NULL DEFAULT 0,
    total_rows   BIGINT       NULL,
    step         VARCHAR(32)  NOT NULL DEFAULT 'pending',
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
