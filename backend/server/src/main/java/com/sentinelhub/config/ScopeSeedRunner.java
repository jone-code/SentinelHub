package com.sentinelhub.config;

import com.sentinelhub.module.device.DeviceRepository;
import com.sentinelhub.module.device.DeviceScopeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds default org unit for demo tenant and assigns existing devices.
 */
@Component
@Order(20)
public class ScopeSeedRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ScopeSeedRunner.class);

    private final SeedProperties seedProperties;
    private final DeviceScopeRepository deviceScopeRepository;
    private final DeviceRepository deviceRepository;
    private final com.sentinelhub.module.identity.UserRepository userRepository;

    public ScopeSeedRunner(SeedProperties seedProperties, DeviceScopeRepository deviceScopeRepository,
                           DeviceRepository deviceRepository,
                           com.sentinelhub.module.identity.UserRepository userRepository) {
        this.seedProperties = seedProperties;
        this.deviceScopeRepository = deviceScopeRepository;
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        userRepository.findTenantIdBySlug(seedProperties.tenantSlug()).ifPresent(tenantId -> {
            if (!deviceScopeRepository.listOrgUnits(tenantId).isEmpty()) {
                return;
            }
            String orgUnitId = deviceScopeRepository.createOrgUnit(tenantId, "默认组织", null);
            deviceRepository.listByTenant(tenantId, 1000, 0).forEach(device ->
                    deviceScopeRepository.assignDeviceOrgUnit(tenantId, device.id(), orgUnitId));
            log.info("Seeded default org unit {} for tenant {}", orgUnitId, seedProperties.tenantSlug());
        });
    }
}
