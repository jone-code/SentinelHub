-- Multi-admin approvals for plan tier changes
CREATE TABLE IF NOT EXISTS plan_change_approvals (
    request_id   CHAR(36)     NOT NULL,
    reviewer_id  CHAR(36)     NOT NULL,
    created_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (request_id, reviewer_id),
    CONSTRAINT fk_plan_approvals_request FOREIGN KEY (request_id) REFERENCES tenant_plan_change_requests(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE tenant_plan_change_requests
    ADD COLUMN billing_external_id VARCHAR(128) NULL AFTER billing_note;
