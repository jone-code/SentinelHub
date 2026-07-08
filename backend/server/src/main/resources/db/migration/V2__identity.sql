-- Identity & audit tables (P0)

CREATE TABLE IF NOT EXISTS org_units (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    parent_id   CHAR(36)     NULL,
    name        VARCHAR(128) NOT NULL,
    path        VARCHAR(512) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at  DATETIME(3)  NULL,
    KEY idx_org_units_tenant_path (tenant_id, path),
    CONSTRAINT fk_org_units_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS users (
    id            CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id     CHAR(36)     NOT NULL,
    org_unit_id   CHAR(36)     NULL,
    email         VARCHAR(256) NOT NULL,
    name          VARCHAR(128) NOT NULL,
    password_hash VARCHAR(256) NULL,
    status        VARCHAR(16)  NOT NULL DEFAULT 'active',
    created_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    deleted_at    DATETIME(3)  NULL,
    UNIQUE KEY uk_users_tenant_email (tenant_id, email),
    CONSTRAINT fk_users_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS roles (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    code        VARCHAR(64)  NOT NULL,
    name        VARCHAR(128) NOT NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    UNIQUE KEY uk_roles_tenant_code (tenant_id, code),
    CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS user_roles (
    user_id CHAR(36) NOT NULL,
    role_id CHAR(36) NOT NULL,
    PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS audit_logs (
    id          CHAR(36)     PRIMARY KEY DEFAULT (UUID()),
    tenant_id   CHAR(36)     NOT NULL,
    actor_type  VARCHAR(16)  NOT NULL,
    actor_id    VARCHAR(64)  NOT NULL,
    action      VARCHAR(128) NOT NULL,
    resource    VARCHAR(128) NULL,
    resource_id VARCHAR(64)  NULL,
    detail      JSON         NOT NULL DEFAULT (JSON_OBJECT()),
    ip_address  VARCHAR(64)  NULL,
    created_at  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    KEY idx_audit_tenant_created (tenant_id, created_at),
    KEY idx_audit_action (tenant_id, action)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE tenants
    ADD COLUMN registration_token VARCHAR(128) NULL AFTER settings;
