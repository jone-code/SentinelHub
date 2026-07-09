-- P3: Zero Trust trust scoring and MDM profiles

CREATE TABLE IF NOT EXISTS zt_policies (
    id                  CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id           CHAR(36)     NOT NULL,
    name                VARCHAR(128) NOT NULL,
    compliance_weight   SMALLINT     NOT NULL DEFAULT 60,
    nac_weight          SMALLINT     NOT NULL DEFAULT 25,
    event_weight        SMALLINT     NOT NULL DEFAULT 15,
    min_trust_score     SMALLINT     NOT NULL DEFAULT 70,
    enabled             TINYINT(1)   NOT NULL DEFAULT 1,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_zt_policies_tenant (tenant_id),
    CONSTRAINT fk_zt_policies_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zt_protected_apps (
    id               CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id        CHAR(36)     NOT NULL,
    name             VARCHAR(128) NOT NULL,
    app_identifier   VARCHAR(256) NOT NULL,
    min_trust_score  SMALLINT     NOT NULL DEFAULT 70,
    enabled          TINYINT(1)   NOT NULL DEFAULT 1,
    created_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_zt_protected_apps_tenant (tenant_id, enabled),
    CONSTRAINT fk_zt_protected_apps_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS zt_trust_history (
    id           CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id    CHAR(36)     NOT NULL,
    device_id    CHAR(36)     NOT NULL,
    trust_score  SMALLINT     NOT NULL,
    factors      JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_zt_trust_history_device (device_id, created_at),
    CONSTRAINT fk_zt_trust_history_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mdm_profiles (
    id           CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id    CHAR(36)     NOT NULL,
    name         VARCHAR(128) NOT NULL,
    profile_type VARCHAR(32)  NOT NULL DEFAULT 'wifi',
    content      JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    enabled      TINYINT(1)   NOT NULL DEFAULT 1,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    KEY idx_mdm_profiles_tenant (tenant_id, enabled),
    CONSTRAINT fk_mdm_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS mdm_device_assignments (
    device_id    CHAR(36)     NOT NULL,
    profile_id   CHAR(36)     NOT NULL,
    tenant_id    CHAR(36)     NOT NULL,
    status       VARCHAR(16)  NOT NULL DEFAULT 'pending',
    assigned_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    applied_at   DATETIME(3)  NULL,
    PRIMARY KEY (device_id, profile_id),
    KEY idx_mdm_assignments_tenant (tenant_id),
    CONSTRAINT fk_mdm_assign_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE,
    CONSTRAINT fk_mdm_assign_profile FOREIGN KEY (profile_id) REFERENCES mdm_profiles(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
