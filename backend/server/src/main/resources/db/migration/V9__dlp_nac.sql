-- DLP rules and NAC policies (P2)

CREATE TABLE IF NOT EXISTS dlp_rules (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    name        VARCHAR(128) NOT NULL,
    channel     VARCHAR(32)  NOT NULL,
    action      VARCHAR(16)  NOT NULL DEFAULT 'alert',
    patterns    JSON         NOT NULL DEFAULT (JSON_ARRAY()),
    enabled     TINYINT(1)   NOT NULL DEFAULT 1,
    priority    INT          NOT NULL DEFAULT 100,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_dlp_rules_tenant (tenant_id, enabled),
    CONSTRAINT fk_dlp_rules_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nac_policies (
    id                    CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id             CHAR(36)     NOT NULL,
    name                  VARCHAR(128) NOT NULL,
    min_compliance_score  SMALLINT     NOT NULL DEFAULT 80,
    action_on_fail        VARCHAR(16)  NOT NULL DEFAULT 'restrict',
    isolate_vlan          VARCHAR(64)  NULL,
    enabled               TINYINT(1)   NOT NULL DEFAULT 1,
    created_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_nac_policies_tenant (tenant_id),
    CONSTRAINT fk_nac_policies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nac_device_status (
    device_id         CHAR(36)     PRIMARY KEY,
    tenant_id         CHAR(36)     NOT NULL,
    policy_id         CHAR(36)     NULL,
    access_state      VARCHAR(16)  NOT NULL DEFAULT 'unknown',
    compliance_score  SMALLINT     NULL,
    detail            JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    evaluated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_nac_device_status_tenant (tenant_id, access_state),
    CONSTRAINT fk_nac_device_status_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_nac_device_status_policy FOREIGN KEY (policy_id) REFERENCES nac_policies(id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
