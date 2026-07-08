-- DLP evidence metadata and NAC RADIUS settings (P2)

CREATE TABLE IF NOT EXISTS dlp_evidence (
    id           CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id    CHAR(36)     NOT NULL,
    device_id    CHAR(36)     NOT NULL,
    rule_id      CHAR(36)     NULL,
    event_id     CHAR(36)     NULL,
    object_key   VARCHAR(512) NOT NULL,
    filename     VARCHAR(256) NOT NULL,
    content_type VARCHAR(128) NOT NULL DEFAULT 'application/octet-stream',
    size_bytes   INT          NOT NULL,
    sha256       VARCHAR(128) NOT NULL,
    channel      VARCHAR(32)  NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_dlp_evidence_tenant (tenant_id, created_at),
    KEY idx_dlp_evidence_device (device_id, created_at),
    CONSTRAINT fk_dlp_evidence_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id),
    CONSTRAINT fk_dlp_evidence_device FOREIGN KEY (device_id) REFERENCES devices(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS nac_radius_settings (
    tenant_id       CHAR(36)     PRIMARY KEY,
    enabled         TINYINT(1)   NOT NULL DEFAULT 0,
    server_host     VARCHAR(256) NULL,
    auth_port       INT          NOT NULL DEFAULT 1812,
    acct_port       INT          NOT NULL DEFAULT 1813,
    secret          VARCHAR(256) NULL,
    nas_identifier  VARCHAR(128) NULL,
    vlan_allowed    VARCHAR(64)  NULL,
    vlan_restricted VARCHAR(64)  NULL,
    vlan_denied     VARCHAR(64)  NULL,
    created_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3),
    CONSTRAINT fk_nac_radius_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
