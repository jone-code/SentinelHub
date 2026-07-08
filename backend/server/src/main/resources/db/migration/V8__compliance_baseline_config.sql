-- Configurable compliance baseline (P1)

ALTER TABLE compliance_baselines
    ADD COLUMN is_active    TINYINT(1)   NOT NULL DEFAULT 1 AFTER framework,
    ADD COLUMN content_hash VARCHAR(128) NULL AFTER rules,
    ADD COLUMN updated_at   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
        ON UPDATE CURRENT_TIMESTAMP(3) AFTER created_at;

UPDATE compliance_baselines SET is_active = 1 WHERE is_active IS NULL;
