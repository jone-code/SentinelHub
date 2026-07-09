-- Client security events (P1)

CREATE TABLE IF NOT EXISTS client_events (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    device_id   CHAR(36)     NOT NULL,
    event_type  VARCHAR(64)  NOT NULL,
    severity    VARCHAR(16)  NOT NULL DEFAULT 'warning',
    detail      JSON         NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_client_events_tenant_created (tenant_id, created_at),
    KEY idx_client_events_device (device_id, created_at),
    CONSTRAINT fk_client_events_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_client_events_device FOREIGN KEY (device_id) REFERENCES devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
