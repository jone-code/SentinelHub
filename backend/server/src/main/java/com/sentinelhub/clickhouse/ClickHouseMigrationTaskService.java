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
    private final ClickHouseMigrationLockService lockService;
    private final Executor executor;

    public ClickHouseMigrationTaskService(ClickHouseSchemaMigrationService migrationService,
                                          ClickHouseMigrationLockService lockService,
                                          @Qualifier("clickHouseMigrationExecutor") Executor executor) {
        this.migrationService = migrationService;
        this.lockService = lockService;
        this.executor = executor;
    }

    public Map<String, Object> submit(String trigger) {
        if (migrationService.isRunning()) {
            throw new IllegalStateException("migration already running");
        }
        if (!lockService.tryAcquire()) {
            throw new IllegalStateException("migration lock held by another instance: " + lockService.instanceId());
        }
        try {
            migrationService.prepareAsyncRun(trigger);
        } catch (Exception e) {
            lockService.release();
            throw e;
        }
        executor.execute(() -> runWithLock(trigger));
        Map<String, Object> snapshot = migrationService.snapshot();
        snapshot.put("lock_holder", lockService.instanceId());
        return snapshot;
    }

    public void submitQuietly(String trigger) {
        if (migrationService.isRunning()) {
            return;
        }
        if (!lockService.tryAcquire()) {
            log.info("Skipping ClickHouse migration ({}): lock held by another instance", trigger);
            return;
        }
        try {
            migrationService.prepareAsyncRun(trigger);
        } catch (Exception e) {
            lockService.release();
            log.warn("Skipping ClickHouse migration ({}): {}", trigger, e.getMessage());
            return;
        }
        executor.execute(() -> runWithLock(trigger));
    }

    private void runWithLock(String trigger) {
        try {
            migrationService.runMigrationBody(trigger);
        } catch (Exception e) {
            log.error("ClickHouse migration failed: {}", e.getMessage());
        } finally {
            lockService.release();
        }
    }
}
