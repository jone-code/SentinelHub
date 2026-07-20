-- Plan tier change requests with approval workflow and billing estimates
CREATE TABLE IF NOT EXISTS tenant_plan_change_requests (
    id                  CHAR(36)     PRIMARY KEY,
    tenant_id           CHAR(36)     NOT NULL,
    requested_by        CHAR(36)     NOT NULL,
    from_tier           VARCHAR(16)  NOT NULL,
    to_tier             VARCHAR(16)  NOT NULL,
    status              VARCHAR(16)  NOT NULL DEFAULT 'pending',
    monthly_price_cents INT          NOT NULL DEFAULT 0,
    currency            VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    billing_note        VARCHAR(512) NULL,
    reviewed_by         CHAR(36)     NULL,
    review_note         VARCHAR(512) NULL,
    created_at          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    reviewed_at         DATETIME(3)  NULL,
    KEY idx_plan_requests_tenant_status (tenant_id, status),
    CONSTRAINT fk_plan_requests_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
