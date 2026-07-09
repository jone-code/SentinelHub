-- Policy engine tables (P1)

CREATE TABLE IF NOT EXISTS policies (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    name        VARCHAR(128) NOT NULL,
    type        VARCHAR(32)  NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'draft',
    priority    INT          NOT NULL DEFAULT 100,
    scope       JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    content     JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    created_by  CHAR(36)     NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_policies_tenant_status (tenant_id, status),
    CONSTRAINT fk_policies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS policy_versions (
    id           CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id    CHAR(36)     NOT NULL,
    policy_id    CHAR(36)     NOT NULL,
    version      INT          NOT NULL,
    content      JSON         NOT NULL,
    published_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    published_by CHAR(36)     NULL,
    UNIQUE KEY uk_policy_versions (policy_id, version),
    CONSTRAINT fk_policy_versions_policy FOREIGN KEY (policy_id) REFERENCES policies(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS tenant_policy_bundles (
    tenant_id    CHAR(36)     PRIMARY KEY,
    version      VARCHAR(64)  NOT NULL,
    content      JSON         NOT NULL,
    content_hash VARCHAR(128) NOT NULL,
    published_at DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_tenant_policy_bundles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
