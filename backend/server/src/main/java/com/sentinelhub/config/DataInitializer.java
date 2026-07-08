package com.sentinelhub.config;

import com.sentinelhub.module.identity.UserRepository;
import com.sentinelhub.module.identity.domain.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final SeedProperties seedProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(SeedProperties seedProperties, UserRepository userRepository,
                           PasswordEncoder passwordEncoder) {
        this.seedProperties = seedProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedProperties.enabled()) {
            return;
        }
        if (userRepository.tenantExists(seedProperties.tenantSlug())) {
            return;
        }

        String tenantId = userRepository.insertTenant(
                "Demo Tenant", seedProperties.tenantSlug(), seedProperties.registrationToken());
        String orgId = userRepository.insertOrgUnit(tenantId, "Root", "/" + tenantId + "/");

        String userId = UUID.randomUUID().toString();
        User admin = new User(
                userId, tenantId, orgId,
                seedProperties.adminEmail(),
                "Administrator",
                passwordEncoder.encode(seedProperties.adminPassword()),
                "active",
                java.time.Instant.now()
        );
        userRepository.insert(admin);

        String roleId = userRepository.insertRole(tenantId, "super_admin", "Super Admin");
        userRepository.assignRole(userId, roleId);

        log.info("Seeded demo tenant '{}' admin={} registration_token={}",
                seedProperties.tenantSlug(), seedProperties.adminEmail(), seedProperties.registrationToken());
    }
}
