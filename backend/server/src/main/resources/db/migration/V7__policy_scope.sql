-- Policy scope: org units and device groups (P1)

CREATE TABLE IF NOT EXISTS org_units (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    parent_id   CHAR(36)     NULL,
    name        VARCHAR(128) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_org_units_tenant (tenant_id),
    CONSTRAINT fk_org_units_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_groups (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    name        VARCHAR(128) NOT NULL,
    description VARCHAR(512) NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_device_groups_tenant (tenant_id),
    CONSTRAINT fk_device_groups_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS device_group_members (
    device_group_id CHAR(36)    NOT NULL,
    device_id       CHAR(36)    NOT NULL,
    added_at        DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (device_group_id, device_id),
    KEY idx_dgm_device (device_id),
    CONSTRAINT fk_dgm_group FOREIGN KEY (device_group_id) REFERENCES device_groups(id) ON DELETE CASCADE,
    CONSTRAINT fk_dgm_device FOREIGN KEY (device_id) REFERENCES devices(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
