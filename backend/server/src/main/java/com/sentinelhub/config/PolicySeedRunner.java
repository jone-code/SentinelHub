package com.sentinelhub.config;

import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.module.policy.PolicyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Seeds demo software policy for existing tenants without policies.
 */
@Component
@Order(2)
public class PolicySeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PolicySeedRunner.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final PolicyService policyService;

    public PolicySeedRunner(SeedProperties seedProperties, UserRepository userRepository,
                            PolicyService policyService) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.policyService = policyService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        Optional<String> tenantId = userRepository.findTenantIdBySlug(seedProperties.tenantSlug());
        tenantId.ifPresent(id -> {
            policyService.seedDemoSoftwarePolicy(id, "system");
            log.info("Ensured demo software policy for tenant {}", seedProperties.tenantSlug());
        });
    }
}
