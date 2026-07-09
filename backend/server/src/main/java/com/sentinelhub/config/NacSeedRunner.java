package com.sentinelhub.config;

import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.module.nac.NacService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(26)
public class NacSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(NacSeedRunner.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final NacService nacService;

    public NacSeedRunner(SeedProperties seedProperties, UserRepository userRepository, NacService nacService) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.nacService = nacService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        userRepository.findTenantIdBySlug(seedProperties.tenantSlug()).ifPresent(tenantId -> {
            nacService.seedDemoPolicy(tenantId);
            nacService.seedRadiusTemplate(tenantId);
            log.info("Ensured demo NAC policy for tenant {}", seedProperties.tenantSlug());
        });
    }
}
