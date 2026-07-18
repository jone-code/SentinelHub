package com.sentinelhub.config;

import com.sentinelhub.clickhouse.ClickHouseSchemaMigrationService;
import com.sentinelhub.module.audit.ClickHouseAuditRepository;
import com.sentinelhub.module.software.ClickHouseClientEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class ClickHouseInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseInitializer.class);

    private final AuditClickHouseProperties properties;
    private final ClickHouseAuditRepository clickHouseAuditRepository;
    private final ClickHouseClientEventRepository clickHouseClientEventRepository;
    private final ClickHouseSchemaMigrationService migrationService;

    public ClickHouseInitializer(AuditClickHouseProperties properties,
                                 ClickHouseAuditRepository clickHouseAuditRepository,
                                 ClickHouseClientEventRepository clickHouseClientEventRepository,
                                 ClickHouseSchemaMigrationService migrationService) {
        this.properties = properties;
        this.clickHouseAuditRepository = clickHouseAuditRepository;
        this.clickHouseClientEventRepository = clickHouseClientEventRepository;
        this.migrationService = migrationService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        clickHouseAuditRepository.ensureSchema();
        clickHouseClientEventRepository.ensureSchema();
        migrationService.migrateToReplacingMergeTreeIfNeeded();
        log.info("ClickHouse cold storage enabled ({}) — audit_logs + client_events", properties.url());
    }
}
