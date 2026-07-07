-- Platform baseline migration: tenants and devices
-- Applied by each service or via deploy/migrations orchestrator

CREATE EXTENSION IF NOT EXISTS "pgcrypto";
CREATE EXTENSION IF NOT EXISTS "ltree";

CREATE TABLE IF NOT EXISTS tenants (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(128) NOT NULL,
    slug        VARCHAR(64) UNIQUE NOT NULL,
    status      VARCHAR(16) NOT NULL DEFAULT 'active',
    settings    JSONB NOT NULL DEFAULT '{}',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS devices (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id       UUID NOT NULL REFERENCES tenants(id),
    org_unit_id     UUID,
    agent_id        VARCHAR(64) NOT NULL,
    hostname        VARCHAR(256),
    os_type         VARCHAR(16) NOT NULL,
    os_version      VARCHAR(64),
    hardware_id     VARCHAR(128) NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'pending',
    last_seen_at    TIMESTAMPTZ,
    compliance_score SMALLINT,
    trust_score     SMALLINT,
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (tenant_id, agent_id)
);

CREATE INDEX IF NOT EXISTS idx_devices_tenant_status ON devices(tenant_id, status);
