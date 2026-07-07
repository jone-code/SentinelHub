-- SentinelHub initial schema (MySQL 8.4)

CREATE TABLE IF NOT EXISTS tenants (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    name        VARCHAR(128) NOT NULL,
    slug        VARCHAR(64)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'active',
    settings    JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_tenants_slug (slug)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS devices (
    id               CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id        CHAR(36)     NOT NULL,
    org_unit_id      CHAR(36)     NULL,
    agent_id         VARCHAR(64)  NOT NULL,
    hostname         VARCHAR(256) NULL,
    os_type          VARCHAR(16)  NOT NULL,
    os_version       VARCHAR(64)  NULL,
    hardware_id      VARCHAR(128) NOT NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'pending',
    last_seen_at     DATETIME(3)  NULL,
    compliance_score SMALLINT     NULL,
    trust_score      SMALLINT     NULL,
    metadata         JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_devices_tenant_agent (tenant_id, agent_id),
    KEY idx_devices_tenant_status (tenant_id, status),
    CONSTRAINT fk_devices_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
