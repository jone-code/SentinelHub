package com.sentinelhub.clickhouse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.Executor;

@Service
public class ClickHouseMigrationTaskService {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseMigrationTaskService.class);

    private final ClickHouseSchemaMigrationService migrationService;
    private final Executor executor;

    public ClickHouseMigrationTaskService(ClickHouseSchemaMigrationService migrationService,
                                          @Qualifier("clickHouseMigrationExecutor") Executor executor) {
        this.migrationService = migrationService;
        this.executor = executor;
    }

    public Map<String, Object> submit(String trigger) {
        if (migrationService.isRunning()) {
            throw new IllegalStateException("migration already running");
        }
        migrationService.prepareAsyncRun(trigger);
        executor.execute(() -> {
            try {
                migrationService.runMigrationBody(trigger);
            } catch (Exception e) {
                log.error("ClickHouse migration failed: {}", e.getMessage());
            }
        });
        return migrationService.snapshot();
    }

    public void submitQuietly(String trigger) {
        if (migrationService.isRunning()) {
            return;
        }
        try {
            migrationService.prepareAsyncRun(trigger);
        } catch (Exception e) {
            log.warn("Skipping ClickHouse migration ({}): {}", trigger, e.getMessage());
            return;
        }
        executor.execute(() -> {
            try {
                migrationService.runMigrationBody(trigger);
            } catch (Exception e) {
                log.error("ClickHouse startup migration failed: {}", e.getMessage());
            }
        });
    }
}
