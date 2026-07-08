package com.sentinelhub.config;

import com.sentinelhub.module.dlp.DlpService;
import com.sentinelhub.module.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(25)
public class DlpSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DlpSeedRunner.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final DlpService dlpService;

    public DlpSeedRunner(SeedProperties seedProperties, UserRepository userRepository, DlpService dlpService) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.dlpService = dlpService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        userRepository.findTenantIdBySlug(seedProperties.tenantSlug()).ifPresent(tenantId -> {
            dlpService.seedDemoRules(tenantId);
            log.info("Ensured demo DLP rules for tenant {}", seedProperties.tenantSlug());
        });
    }
}
