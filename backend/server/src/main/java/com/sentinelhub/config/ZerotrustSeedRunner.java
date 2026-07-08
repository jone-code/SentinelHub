package com.sentinelhub.config;

import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.module.zerotrust.ZerotrustService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(27)
public class ZerotrustSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ZerotrustSeedRunner.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final ZerotrustService zerotrustService;

    public ZerotrustSeedRunner(SeedProperties seedProperties, UserRepository userRepository,
                               ZerotrustService zerotrustService) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.zerotrustService = zerotrustService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        userRepository.findTenantIdBySlug(seedProperties.tenantSlug()).ifPresent(tenantId -> {
            zerotrustService.seedDemoPolicy(tenantId);
            log.info("Ensured demo Zero Trust policy for tenant {}", seedProperties.tenantSlug());
        });
    }
}
