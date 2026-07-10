package com.sentinelhub.config;

import com.sentinelhub.module.audit.ClickHouseAuditRepository;
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

    public ClickHouseInitializer(AuditClickHouseProperties properties,
                                 ClickHouseAuditRepository clickHouseAuditRepository) {
        this.properties = properties;
        this.clickHouseAuditRepository = clickHouseAuditRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        clickHouseAuditRepository.ensureSchema();
        log.info("ClickHouse audit cold storage enabled ({})", properties.url());
    }
}
