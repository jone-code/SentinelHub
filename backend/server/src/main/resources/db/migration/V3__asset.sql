-- Asset inventory tables (P0)

CREATE TABLE IF NOT EXISTS asset_hardware (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    device_id   CHAR(36)     NOT NULL,
    hostname    VARCHAR(256) NULL,
    os_type     VARCHAR(16)  NOT NULL,
    os_version  VARCHAR(64)  NULL,
    arch        VARCHAR(32)  NULL,
    cpu_model   VARCHAR(256) NULL,
    cpu_cores   INT          NULL,
    memory_mb   INT          NULL,
    raw_data    JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    collected_at DATETIME(3) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_asset_hw_device (device_id),
    KEY idx_asset_hw_tenant (tenant_id),
    CONSTRAINT fk_asset_hw_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_asset_hw_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS asset_software (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    device_id   CHAR(36)     NOT NULL,
    name        VARCHAR(256) NOT NULL,
    version     VARCHAR(128) NULL,
    collected_at DATETIME(3) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_asset_sw_device (device_id),
    KEY idx_asset_sw_tenant_name (tenant_id, name),
    CONSTRAINT fk_asset_sw_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_asset_sw_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
