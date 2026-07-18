-- Timeline hot-to-cold incremental sync watermarks
CREATE TABLE IF NOT EXISTS timeline_sync_state (
    sync_key    VARCHAR(64) PRIMARY KEY,
    watermark_at TIMESTAMP(3) NOT NULL,
    updated_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);

INSERT INTO timeline_sync_state (sync_key, watermark_at)
VALUES ('audit_logs', '1970-01-01 00:00:00.000'),
       ('client_events', '1970-01-01 00:00:00.000')
ON DUPLICATE KEY UPDATE sync_key = sync_key;
