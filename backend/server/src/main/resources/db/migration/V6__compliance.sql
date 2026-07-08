-- Compliance baselines and scan results (P1)

CREATE TABLE IF NOT EXISTS compliance_baselines (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    name        VARCHAR(128) NOT NULL,
    framework   VARCHAR(32)  NULL,
    rules       JSON         NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_compliance_baselines_tenant (tenant_id),
    CONSTRAINT fk_compliance_baselines_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS compliance_results (
    id           CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id    CHAR(36)     NOT NULL,
    device_id    CHAR(36)     NOT NULL,
    baseline_id  CHAR(36)     NOT NULL,
    score        SMALLINT     NOT NULL,
    passed       INT          NOT NULL DEFAULT 0,
    failed       INT          NOT NULL DEFAULT 0,
    details      JSON         NOT NULL,
    scanned_at   DATETIME(3)  NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_compliance_results_device (device_id, scanned_at),
    KEY idx_compliance_results_tenant (tenant_id, scanned_at),
    CONSTRAINT fk_compliance_results_device FOREIGN KEY (device_id) REFERENCES devices(id),
    CONSTRAINT fk_compliance_results_baseline FOREIGN KEY (baseline_id) REFERENCES compliance_baselines(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
