package com.sentinelhub.config;

import com.sentinelhub.module.compliance.ComplianceService;
import com.sentinelhub.module.identity.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(3)
public class ComplianceSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ComplianceSeedRunner.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final ComplianceService complianceService;

    public ComplianceSeedRunner(SeedProperties seedProperties, UserRepository userRepository,
                                ComplianceService complianceService) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.complianceService = complianceService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        userRepository.findTenantIdBySlug(seedProperties.tenantSlug()).ifPresent(tenantId -> {
            complianceService.seedDefaultBaseline(tenantId);
            log.info("Ensured default compliance baseline for tenant {}", seedProperties.tenantSlug());
        });
    }
}
