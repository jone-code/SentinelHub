-- Tenant plan tier for per-tenant WebSocket quotas
ALTER TABLE tenants
    ADD COLUMN plan_tier VARCHAR(16) NOT NULL DEFAULT 'starter' AFTER status;

CREATE INDEX idx_tenants_plan_tier ON tenants (plan_tier);
