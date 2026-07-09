package com.sentinelhub.config;

import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.module.mdm.MdmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(28)
public class MdmSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MdmSeedRunner.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final MdmService mdmService;

    public MdmSeedRunner(SeedProperties seedProperties, UserRepository userRepository, MdmService mdmService) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.mdmService = mdmService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        userRepository.findTenantIdBySlug(seedProperties.tenantSlug()).ifPresent(tenantId -> {
            mdmService.seedDemoProfiles(tenantId);
            log.info("Ensured demo MDM profiles for tenant {}", seedProperties.tenantSlug());
        });
    }
}
