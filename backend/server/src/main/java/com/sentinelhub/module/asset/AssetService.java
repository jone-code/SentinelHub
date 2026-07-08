package com.sentinelhub.module.asset;

import com.sentinelhub.module.audit.AuditService;
import com.sentinelhub.module.device.DeviceRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class AssetService {

    private final AssetRepository assetRepository;
    private final DeviceRepository deviceRepository;
    private final AuditService auditService;

    public AssetService(AssetRepository assetRepository, DeviceRepository deviceRepository,
                        AuditService auditService) {
        this.assetRepository = assetRepository;
        this.deviceRepository = deviceRepository;
        this.auditService = auditService;
    }

    @SuppressWarnings("unchecked")
    public void ingestReport(String tenantId, String deviceId, Map<String, Object> assets) {
        String collectedAt = str(assets.get("collected_at"));
        if (collectedAt == null) {
            collectedAt = java.time.Instant.now().toString();
        }

        if (assets.get("hardware") instanceof Map<?, ?> hw) {
            assetRepository.upsertHardware(tenantId, deviceId, (Map<String, Object>) hw, collectedAt);
        }

        if (assets.get("software") instanceof List<?> sw) {
            List<Map<String, Object>> items = sw.stream()
                    .filter(Map.class::isInstance)
                    .map(m -> (Map<String, Object>) m)
                    .toList();
            assetRepository.replaceSoftware(tenantId, deviceId, items, collectedAt);
        }

        auditService.log(tenantId, "agent", deviceId, "asset.report", "device", deviceId,
                Map.of("software_count", countSoftware(assets)), null);
    }

    public Map<String, Object> getDeviceAssets(String tenantId, String deviceId) {
        deviceRepository.findById(tenantId, deviceId)
                .orElseThrow(() -> new IllegalArgumentException("device not found"));
        return Map.of(
                "hardware", assetRepository.getHardware(deviceId),
                "software", assetRepository.getSoftware(deviceId)
        );
    }

    private static String str(Object o) {
        return o != null ? o.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private static int countSoftware(Map<String, Object> assets) {
        if (assets.get("software") instanceof List<?> list) return list.size();
        return 0;
    }
}
