-- P4: AI insights and remote WebRTC signaling

CREATE TABLE IF NOT EXISTS ai_insights (
    id                CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id         CHAR(36)     NOT NULL,
    insight_type      VARCHAR(32)  NOT NULL,
    severity          VARCHAR(16)  NOT NULL DEFAULT 'info',
    title             VARCHAR(256) NOT NULL,
    summary           TEXT         NULL,
    evidence          JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    related_device_id CHAR(36)     NULL,
    status            VARCHAR(16)  NOT NULL DEFAULT 'open',
    created_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_ai_insights_tenant_status (tenant_id, status, created_at),
    CONSTRAINT fk_ai_insights_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_ai_insights_device FOREIGN KEY (related_device_id) REFERENCES devices(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS remote_signaling (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    session_id  CHAR(36)     NOT NULL,
    role        VARCHAR(16)  NOT NULL,
    sdp_type    VARCHAR(16)  NOT NULL,
    sdp_payload TEXT         NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_remote_signaling_session (session_id, created_at),
    CONSTRAINT fk_remote_signaling_session FOREIGN KEY (session_id) REFERENCES remote_sessions(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
