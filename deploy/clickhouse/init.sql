CREATE DATABASE IF NOT EXISTS sentinelhub;

CREATE TABLE IF NOT EXISTS sentinelhub.audit_logs
(
    id          String,
    tenant_id   String,
    actor_type  String,
    actor_id    String,
    action      String,
    resource    Nullable(String),
    resource_id Nullable(String),
    detail      String,
    ip_address  Nullable(String),
    created_at  DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
ORDER BY (tenant_id, created_at)
TTL created_at + INTERVAL 365 DAY;

CREATE TABLE IF NOT EXISTS sentinelhub.client_events
(
    id          String,
    tenant_id   String,
    device_id   String,
    event_type  String,
    severity    String,
    detail      String,
    created_at  DateTime64(3) DEFAULT now64(3)
)
ENGINE = MergeTree()
ORDER BY (tenant_id, created_at)
TTL created_at + INTERVAL 365 DAY;
