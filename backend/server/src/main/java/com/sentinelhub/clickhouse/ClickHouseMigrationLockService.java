package com.sentinelhub.clickhouse;

import com.sentinelhub.config.AuditClickHouseProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ClickHouseMigrationLockService {

    private static final Logger log = LoggerFactory.getLogger(ClickHouseMigrationLockService.class);

    private final ClickHouseMigrationLockRepository lockRepository;
    private final String instanceId;
    private final int ttlSeconds;

    public ClickHouseMigrationLockService(ClickHouseMigrationLockRepository lockRepository,
                                          String sentinelInstanceId,
                                          AuditClickHouseProperties properties) {
        this.lockRepository = lockRepository;
        this.instanceId = sentinelInstanceId;
        this.ttlSeconds = properties.migrationLockTtlSeconds();
    }

    public boolean tryAcquire() {
        lockRepository.deleteExpired();
        var existing = lockRepository.find();
        if (existing.isEmpty()) {
            boolean acquired = lockRepository.insert(instanceId, ttlSeconds);
            if (acquired) {
                log.info("Acquired ClickHouse migration lock as {}", instanceId);
            }
            return acquired;
        }
        if (instanceId.equals(existing.get().holderId())) {
            lockRepository.renew(instanceId, ttlSeconds);
            return true;
        }
        log.info("ClickHouse migration lock held by {} until {}", existing.get().holderId(), existing.get().expiresAt());
        return false;
    }

    public void renew() {
        lockRepository.renew(instanceId, ttlSeconds);
    }

    public void release() {
        lockRepository.release(instanceId);
        log.info("Released ClickHouse migration lock for {}", instanceId);
    }

    public String instanceId() {
        return instanceId;
    }
}
