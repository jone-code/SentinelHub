package com.sentinelhub.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sentinelhub.audit.clickhouse")
public record AuditClickHouseProperties(
        boolean enabled,
        String url,
        String database,
        boolean replacingMerge,
        boolean replacingMergeMigrateOnStartup,
        int migrationBatchSize,
        boolean migrationResumeEnabled
) {
    public AuditClickHouseProperties {
        if (url == null || url.isBlank()) {
            url = "http://localhost:8123";
        }
        if (database == null || database.isBlank()) {
            database = "sentinelhub";
        }
        if (migrationBatchSize <= 0) {
            migrationBatchSize = 10000;
        }
    }
}
