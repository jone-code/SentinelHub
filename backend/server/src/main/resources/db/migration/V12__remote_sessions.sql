-- P4: Remote assistance sessions

CREATE TABLE IF NOT EXISTS remote_sessions (
    id               CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id        CHAR(36)     NOT NULL,
    device_id        CHAR(36)     NOT NULL,
    operator_user_id CHAR(36)     NULL,
    operator_name    VARCHAR(128) NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'pending',
    reason           VARCHAR(512) NULL,
    consent_required TINYINT(1)   NOT NULL DEFAULT 1,
    consented_at     DATETIME(3)  NULL,
    started_at       DATETIME(3)  NULL,
    ended_at         DATETIME(3)  NULL,
    recording_key    VARCHAR(512) NULL,
    meta             JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_remote_sessions_tenant_status (tenant_id, status),
    KEY idx_remote_sessions_device_status (device_id, status),
    CONSTRAINT fk_remote_sessions_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_remote_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
